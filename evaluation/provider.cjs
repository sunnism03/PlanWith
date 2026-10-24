const {spawn}=require('node:child_process');
const fs=require('node:fs'),path=require('node:path');
class JavaProvider {
  constructor(options) { this.mode=options.config.mode; }
  id() { return `planwith-${this.mode}`; }
  async callApi(prompt,context) {
    if(!process.env.OPENAI_API_KEY) return {error:'EVAL_INFRASTRUCTURE: OPENAI_API_KEY missing; NOT_RUN'};
    let cp; try { cp=fs.readFileSync(path.join(__dirname,'../build/evaluation-classpath.txt'),'utf8').trim(); }
    catch { return {error:'EVAL_INFRASTRUCTURE: run ./gradlew evaluationClasspath'}; }
    // Strict allowlist: never forward expected labels, test IDs, difficulty, or assertions to the model.
    const vars=context.vars;
    const request={user_input:vars.user_input,reference_date:vars.reference_date,timezone:vars.timezone,mode:this.mode};
    return new Promise(resolve=>{
      const child=spawn(process.env.JAVA_BIN||'java',['-cp',cp,'com.JAI.chatbot.evaluation.ExtractionHarness'],{stdio:['pipe','pipe','pipe']});
      let stdout='';let done=false;
      const finish=result=>{if(done)return;done=true;clearTimeout(timer);resolve(result);};
      const timer=setTimeout(()=>{child.kill('SIGKILL');finish({error:'EVAL_INFRASTRUCTURE: harness timeout'});},55000);
      child.stdout.on('data',b=>{stdout+=b;if(stdout.length>2_000_000){child.kill();finish({error:'EVAL_INFRASTRUCTURE: output limit'});}});
      child.stderr.on('data',()=>{}); // Upstream response bodies and credentials never enter CI annotations.
      child.on('error',()=>finish({error:'EVAL_INFRASTRUCTURE: harness startup failed'}));
      child.on('close',code=>{
        if(code!==0) return finish({error:'EVAL_INFRASTRUCTURE: harness failed'});
        try {
          const output=JSON.parse(stdout.trim().split('\n').at(-1));
          if(/^API_|CALL_BUDGET/.test(output.status)||output.error_category==='EVAL_INFRASTRUCTURE') return finish({error:`EVAL_INFRASTRUCTURE: ${output.status}`});
          finish({output:JSON.stringify(output)});
        } catch {finish({error:'EVAL_INFRASTRUCTURE: invalid harness result'});}
      });
      child.stdin.on('error',()=>{}); child.stdin.end(JSON.stringify(request));
    });
  }
}
module.exports=JavaProvider;
