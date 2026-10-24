const fs=require('node:fs'),path=require('node:path');
const {load,select,validate}=require('./dataset.cjs');
const exploratory=process.argv.includes('--exploratory');
const rows=load();const selected=select(rows,exploratory);
const config={description:`PlanWith ${exploratory?'EXPLORATORY DRAFT LABELS - NOT A QUALITY GATE':'reviewed holdout'}`,
  prompts:['{{user_input}}'],providers:['baseline','candidate'].map(mode=>({id:'file://../provider.cjs',label:mode,config:{mode}})),
  tests:selected.map(r=>({description:r.test_id,vars:{test_id:r.test_id,user_input:r.user_input,reference_date:r.reference_date,timezone:r.timezone,label:r},assert:[{type:'javascript',value:'file://../assertion.cjs'}]})),
  sharing:false,writeLatestResults:false,evaluateOptions:{maxConcurrency:1,repeat:3,cache:false,timeoutMs:60000},metadata:{exploratory,dataset:validate(rows)}};
fs.mkdirSync(path.join(__dirname,'generated'),{recursive:true});
fs.writeFileSync(path.join(__dirname,'generated/promptfooconfig.json'),JSON.stringify(config,null,2));
console.log(`Prepared ${selected.length} ${exploratory?'draft development':'reviewed holdout'} cases; 3 repeats per provider.`);
