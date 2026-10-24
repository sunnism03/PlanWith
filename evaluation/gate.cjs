const {aggregate}=require('./metrics.cjs');
const CRITICAL=['intent_macro_f1','date_exact_match','time_exact_match','item_f1','full_extraction_success_rate','schema_valid_rate'];
// Cluster by test ID to retain correlation across repeats and paired providers.
function interval(pairs,metric,samples=500) {
  const ids=[...new Set(pairs.map(p=>p.label.test_id))];
  let seed=42; const random=()=>{seed=(1664525*seed+1013904223)>>>0;return seed/4294967296;};
  const groups=new Map(ids.map(id=>[id,pairs.filter(p=>p.label.test_id===id)]));
  const deltas=[];
  for(let i=0;i<samples;i++) {
    const draw=[];for(let j=0;j<ids.length;j++) draw.push(...groups.get(ids[Math.floor(random()*ids.length)]));
    const a=aggregate(draw.filter(p=>p.mode==='baseline'))[metric],b=aggregate(draw.filter(p=>p.mode==='candidate'))[metric];
    if(a!==null&&b!==null)deltas.push(b-a);
  }
  deltas.sort((a,b)=>a-b);
  return deltas.length?[deltas[Math.floor(deltas.length*.025)],deltas[Math.min(deltas.length-1,Math.floor(deltas.length*.975))]]:null;
}
function gate(pairs,expectedIds,{exploratory=false,infrastructureErrors=[]}={}) {
  if(infrastructureErrors.length) return {status:'INFRASTRUCTURE_FAILURE',reasons:infrastructureErrors};
  if(exploratory) return {status:'NOT_A_QUALITY_GATE',reasons:['Unreviewed development labels; exploratory results only']};
  if(!expectedIds.length) return {status:'NOT_RUN',reasons:['No human-reviewed holdout labels']};
  if(pairs.some(p=>p.label.label_review_status!=='reviewed'||p.label.split!=='holdout')) return {status:'INVALID_LABELS',reasons:['Only reviewed holdout labels allowed']};
  const actualIds=new Set(pairs.map(p=>p.label.test_id));
  if(actualIds.size!==expectedIds.length||expectedIds.some(id=>!actualIds.has(id))) return {status:'INFRASTRUCTURE_FAILURE',reasons:['Missing or unexpected test IDs']};
  for(const id of expectedIds) {
    const b=pairs.filter(p=>p.label.test_id===id&&p.mode==='baseline').length,c=pairs.filter(p=>p.label.test_id===id&&p.mode==='candidate').length;
    if(b<3||b!==c)return {status:'INFRASTRUCTURE_FAILURE',reasons:['Need >=3 equal repeats per case/provider']};
  }
  const base=aggregate(pairs.filter(p=>p.mode==='baseline')),candidate=aggregate(pairs.filter(p=>p.mode==='candidate'));
  const reasons=[],comparison={};let status='PASS';
  // Returning READY with invalid structure/business rules violates a runtime safety invariant.
  if(pairs.some(p=>p.mode==='candidate'&&p.output.status==='READY'&&(!p.output.schema_valid||!p.output.business_valid))) {
    status='FAIL';reasons.push('Candidate returned invalid READY data');
  }
  for(const metric of CRITICAL) {
    const a=base[metric],b=candidate[metric];
    if(a===null||b===null){if(status!=='FAIL')status='INCONCLUSIVE';reasons.push(`${metric}: no applicable samples`);continue;}
    const delta=b-a,ci=interval(pairs,metric); comparison[metric]={baseline:a,candidate:b,delta,paired_cluster_95_ci:ci};
    if(delta<=-0.02) {
      if(ci&&ci[1]<=-0.02) {status='FAIL';reasons.push(`${metric}: confirmed >=2 percentage point regression`);}
      else {if(status!=='FAIL')status='INCONCLUSIVE';reasons.push(`${metric}: possible regression; more independent repeats/review required`);}
    }
  }
  return {status,reasons,comparison};
}
module.exports={gate,interval,CRITICAL};
