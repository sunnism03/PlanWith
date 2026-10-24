const normalize = s => String(s).normalize('NFC').trim().replace(/\s+/g, ' ');
const value = (item, key) => Object.hasOwn(item, key) ? item[key] : '__MISSING__';
const fields = intent => intent === 'EVENT' ? ['startDate','endDate','startTime','endTime'] : ['date','time'];
function matches(expected, actual, keys) {
  const names = [expected.title, ...(expected.title_aliases || [])].map(normalize);
  return actual && typeof actual.title === 'string' && names.includes(normalize(actual.title)) && keys.every(k => value(expected,k) === value(actual,k));
}
// Maximum bipartite matching: each predicted item can match only once, independent of order.
function matched(expected, actual, keys) {
  const owners = Array(actual.length).fill(-1);
  function visit(i, seen) {
    for (let j=0;j<actual.length;j++) {
      if (seen.has(j) || !matches(expected[i],actual[j],keys)) continue;
      seen.add(j);
      if (owners[j] === -1 || visit(owners[j],seen)) { owners[j]=i; return true; }
    }
    return false;
  }
  return expected.reduce((sum,_,i)=>sum+Number(visit(i,new Set())),0);
}
const ratio=(n,d)=>d ? n/d : null;
function score(label, output) {
  const expected=label.expected_items, actual=Array.isArray(output.items)?output.items:[];
  const keys=fields(label.expected_intent);
  const applicable=label.expected_status==='READY';
  const itemMatches=applicable?matched(expected,actual,keys):0;
  const dateMatches=applicable?matched(expected,actual,keys.filter(k=>/date/i.test(k))):0;
  const timeMatches=applicable?matched(expected,actual,keys.filter(k=>/time/i.test(k))):0;
  const intent=output.intent===label.expected_intent;
  const full=intent && output.status===label.expected_status && output.business_valid===true &&
    (applicable ? itemMatches===expected.length && actual.length===expected.length && output.schema_valid===true : actual.length===0);
  return {intent,full,applicable,itemMatches,dateMatches,timeMatches,expected:expected.length,actual:actual.length};
}
function aggregate(pairs) {
  const classes=['EVENT','TODO','PLAN_RECOMMENDATION','EXCEPTION'];
  const matrix=Object.fromEntries(classes.map(c=>[c,Object.fromEntries([...classes,'INVALID'].map(a=>[a,0]))]));
  let promptTokens=0,completionTokens=0,measuredCalls=0,schemaOutputs=0,rawOutputs=0;
  let hits=0, gold=0,pred=0,dates=0,times=0,full=0,schema=0,rawSchema=0,initialFailures=0,recovered=0,finalFailures=0,calls=0,latency=0,cost=0,costMeasured=true;
  for(const {label,output} of pairs) {
    const s=score(label,output); matrix[label.expected_intent][classes.includes(output.intent)?output.intent:'INVALID']++;
    schemaOutputs+=typeof output.schema_valid==='boolean';rawOutputs+=typeof output.raw_schema_valid==='boolean';
    full+=s.full; schema+=output.schema_valid===true; rawSchema+=output.raw_schema_valid===true;
    if(s.applicable) {hits+=s.itemMatches; gold+=s.expected; pred+=s.actual;dates+=s.dateMatches;times+=s.timeMatches;}
    // Operational failure, distinct from semantic full-extraction failure.
    const failed=!['READY','AMBIGUOUS_INPUT','UNSUPPORTED_RECURRENCE'].includes(output.status);
    const initial=Boolean(output.initial_error) || failed;
    initialFailures+=initial; recovered+=initial && output.status==='READY'; finalFailures+=failed;
    promptTokens+=output.usage?.prompt_tokens||0;completionTokens+=output.usage?.completion_tokens||0;measuredCalls+=output.usage?.measured_calls||0;
    calls+=output.calls||0;latency+=output.latency_ms||0;
    if(typeof output.cost==='number') cost+=output.cost; else costMeasured=false;
  }
  const perClass=Object.fromEntries(classes.map(c=>{
    const tp=matrix[c][c],support=Object.values(matrix[c]).reduce((a,b)=>a+b,0), predicted=classes.reduce((n,x)=>n+matrix[x][c],0);
    const precision=predicted?tp/predicted:0,recall=support?tp/support:0;
    return [c,{precision,recall,f1:precision+recall?2*precision*recall/(precision+recall):0,support}];
  }));
  const supported=Object.values(perClass).filter(c=>c.support>0);
  const p=pred?hits/pred:gold?0:null,r=ratio(hits,gold);
  return {count:pairs.length,intent_macro_f1:supported.length?supported.reduce((n,c)=>n+c.f1,0)/supported.length:null,
    per_class:perClass,confusion_matrix:matrix,date_exact_match:ratio(dates,gold),time_exact_match:ratio(times,gold),
    item_precision:p,item_recall:r,item_f1:p===null||r===null?null:p+r?2*p*r/(p+r):0,
    full_extraction_success_rate:ratio(full,pairs.length),schema_valid_rate:ratio(schema,schemaOutputs),raw_schema_valid_rate:ratio(rawSchema,rawOutputs),
    recurrence_accuracy:null,recurrence_reason:'No recurrence persistence model; unsupported-policy outcomes are scored by full extraction.',
    runtime_recovery_rate:ratio(recovered,initialFailures),initial_error_rate:ratio(initialFailures,pairs.length),final_failure_rate:ratio(finalFailures,pairs.length),
    usage:{prompt_tokens:promptTokens,completion_tokens:completionTokens,measured_calls:measuredCalls,complete:measuredCalls===calls},
    additional_calls:pairs.reduce((n,{output})=>n+Math.max(0,(output.calls||0)-2),0),calls,latency_ms:latency,cost:costMeasured?cost:null,
    denominators:{schema_outputs:schemaOutputs,raw_outputs:rawOutputs,items:gold,predicted_items:pred,initial_failures:initialFailures,requests:pairs.length}};
}
module.exports={normalize,matched,score,aggregate};
