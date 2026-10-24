const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),{execFileSync}=require('node:child_process');
const {load,validate}=require('./dataset.cjs'),{aggregate,score}=require('./metrics.cjs'),{gate}=require('./gate.cjs');
function report(document,rows) {
  const entries=document.results?.results || document.results;
  if(!Array.isArray(entries))throw Error('EVAL_INFRASTRUCTURE: unrecognized Promptfoo result schema');
  const pairs=[],errors=[];
  const labels=new Map(rows.map(r=>[r.test_id,r]));
  for(const e of entries) {
    const id=e.testCase?.vars?.test_id;
    const label=labels.get(id);
    if(!label){errors.push('Unknown test ID');continue;}
    if(e.response?.error || (e.error && !e.response?.output)){errors.push(`${id}: evaluation infrastructure error`);continue;}
    try {
      const output=typeof e.response.output==='string'?JSON.parse(e.response.output):e.response.output;
      if(!['candidate','baseline'].includes(output.mode))throw Error('mode');
      if(/^API_|CALL_BUDGET/.test(output.status)||output.error_category==='EVAL_INFRASTRUCTURE'){errors.push(`${id}: upstream API unavailable`);continue;}
      pairs.push({label,output,mode:output.mode});
    } catch {errors.push(`${id}: missing/invalid output`);}
  }
  if(pairs.some(p=>p.output.model==='MOCK')) errors.push('Mock output cannot be a real LLM quality result');
  const models=new Set(pairs.map(p=>p.output.model));
  if(models.size!==1 || [...models].some(m=>typeof m!=='string')) errors.push('Comparable results require one recorded model');
  for(const mode of ['baseline','candidate']) {
    if(new Set(pairs.filter(p=>p.mode===mode).map(p=>p.output.prompt_version)).size!==1) errors.push(`${mode}: inconsistent prompt versions`);
  }
  const exploratory=pairs.some(p=>p.label.split!=='holdout'||p.label.label_review_status!=='reviewed');
  const holdout=rows.filter(r=>r.split==='holdout');
  const ids=holdout.filter(r=>r.label_review_status==='reviewed').map(r=>r.test_id);
  if(!exploratory&&ids.length<holdout.length)errors.push('Holdout review incomplete; prevent cherry-picking eligible labels');
  const modes=Object.fromEntries(['baseline','candidate'].map(m=>[m,aggregate(pairs.filter(p=>p.mode===m))]));
  const raw=Object.fromEntries(['baseline','candidate'].map(m=>[m,aggregate(pairs.filter(p=>p.mode===m&&typeof p.output.raw_schema_valid==='boolean').map(p=>{
    const r=p.output.raw_output||{};return {...p,output:{intent:p.output.intent,status:r.status||(p.output.raw_schema_valid?'READY':'SCHEMA_INVALID'),items:r.events||r.todos||[],schema_valid:p.output.raw_schema_valid,business_valid:p.output.raw_business_valid===true}};
  }))]));
  const decision=gate(pairs,ids,{exploratory,infrastructureErrors:errors});
  return {evaluation_kind:exploratory?'EXPLORATORY_DRAFT_LABELS':'REVIEWED_HOLDOUT',reviewed_evaluation_cases:ids.length,executed_at:new Date().toISOString(),dataset:validate(rows),dataset_sha256:crypto.createHash('sha256').update(JSON.stringify(rows)).digest('hex'),
    gate:decision,metrics:modes,raw_metrics:raw,raw_note:'First generation is independently checked by the same validator; no correction or regeneration is applied in raw metrics.',
    models:[...new Set(pairs.map(p=>p.output.model))],prompt_versions:[...new Set(pairs.map(p=>p.output.prompt_version))],
    failures:pairs.filter(p=>!score(p.label,p.output).full).map(p=>({test_id:p.label.test_id,mode:p.mode,error_category:p.output.error_category||'SEMANTIC_MISMATCH',expected:{intent:p.label.expected_intent,status:p.label.expected_status,items:p.label.expected_items},actual:{intent:p.output.intent,status:p.output.status,items:p.output.items},calls:p.output.calls,cost:p.output.cost}))};
}
if(require.main===module) {
  try {
    const output=report(JSON.parse(fs.readFileSync(process.argv[2],'utf8')),load());
    output.commit_sha=execFileSync('git',['rev-parse','HEAD'],{encoding:'utf8'}).trim();
    fs.writeFileSync(path.join(__dirname,'reports/summary.json'),JSON.stringify(output,null,2));
    console.log(JSON.stringify({gate:output.gate,dataset:output.dataset},null,2));
    if(output.gate.status!=='PASS')process.exitCode=2;
  }catch(e){console.error('EVAL_INFRASTRUCTURE: report unavailable');process.exitCode=3;}
}
module.exports={report};
