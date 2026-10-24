const {score}=require('./metrics.cjs');
module.exports=(output,context)=>{
  try {const s=score(context.vars.label,typeof output==='string'?JSON.parse(output):output);return {pass:s.full,score:Number(s.full),reason:s.full?'Full extraction matches':'Extraction differs from reviewed/draft label; see aggregate report'};}
  catch {return {pass:false,score:0,reason:'Invalid harness output'};}
};
