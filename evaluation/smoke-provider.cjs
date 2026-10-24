// Mock-only integration of Promptfoo provider -> assertion -> result format. NOT LLM accuracy.
module.exports=class {
  constructor(options){this.mode=options.config.mode;}
  id(){return `mock-${this.mode}`;}
  async callApi(){return {output:JSON.stringify({mode:this.mode,model:'MOCK',prompt_version:'MOCK',intent:'TODO',status:'READY',items:[{title:'독서',date:'2026-10-24',time:null}],schema_valid:true,business_valid:true,calls:0,cost:0})};}
};
