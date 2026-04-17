#!/usr/bin/env python3
"""
Generate examples/compare.html — a self-contained, dependency-free diff viewer
for all 48 Prince of Space showroom outputs.

Usage:
    python3 scripts/generate-compare.py

Run after regenerating showroom goldens:
    REGENERATE_SHOWROOM=true ./gradlew :core:test --tests RegenerateShowroomGoldens
    python3 scripts/generate-compare.py
"""

import json
import sys
from pathlib import Path

REPO = Path(__file__).parent.parent
OUTPUTS = REPO / "examples" / "outputs"
OUT_FILE = REPO / "examples" / "compare.html"

JAVA_LEVELS = ["java8", "java17", "java21", "java25"]

CONFIGS = [
    "balanced-cont4-closingparen-true",
    "balanced-cont4-closingparen-false",
    "balanced-cont8-closingparen-true",
    "balanced-cont8-closingparen-false",
    "narrow-cont4-closingparen-true",
    "narrow-cont4-closingparen-false",
    "narrow-cont8-closingparen-true",
    "narrow-cont8-closingparen-false",
    "wide-cont4-closingparen-true",
    "wide-cont4-closingparen-false",
    "wide-cont8-closingparen-true",
    "wide-cont8-closingparen-false",
]

LABELS = {
    "balanced-cont4-closingparen-true":  "Balanced · indent 4 · ) on new line",
    "balanced-cont4-closingparen-false": "Balanced · indent 4 · ) inline",
    "balanced-cont8-closingparen-true":  "Balanced · indent 8 · ) on new line",
    "balanced-cont8-closingparen-false": "Balanced · indent 8 · ) inline",
    "narrow-cont4-closingparen-true":    "Narrow · indent 4 · ) on new line",
    "narrow-cont4-closingparen-false":   "Narrow · indent 4 · ) inline",
    "narrow-cont8-closingparen-true":    "Narrow · indent 8 · ) on new line",
    "narrow-cont8-closingparen-false":   "Narrow · indent 8 · ) inline",
    "wide-cont4-closingparen-true":      "Wide · indent 4 · ) on new line",
    "wide-cont4-closingparen-false":     "Wide · indent 4 · ) inline",
    "wide-cont8-closingparen-true":      "Wide · indent 8 · ) on new line",
    "wide-cont8-closingparen-false":     "Wide · indent 8 · ) inline",
}


def load_outputs():
    data = {}
    for level in JAVA_LEVELS:
        data[level] = {}
        for config in CONFIGS:
            path = OUTPUTS / level / f"{config}.java"
            if not path.exists():
                print(f"WARNING: missing {path}", file=sys.stderr)
                data[level][config] = ""
            else:
                data[level][config] = path.read_text()
    return data


HTML = r"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Prince of Space — Output Comparison</title>
<style>
*,*::before,*::after{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif;
  background:#0d1117;color:#e6edf3;height:100vh;display:flex;flex-direction:column;overflow:hidden}
header{padding:10px 14px;border-bottom:1px solid #30363d;display:flex;
  align-items:center;gap:12px;flex-wrap:wrap;background:#161b22;flex-shrink:0}
h1{font-size:14px;font-weight:600;color:#f0f6fc;white-space:nowrap}
.controls{display:flex;align-items:center;gap:8px;flex-wrap:wrap}
label{font-size:12px;color:#8b949e;white-space:nowrap}
select{background:#21262d;border:1px solid #30363d;color:#e6edf3;border-radius:6px;
  padding:4px 8px;font-size:12px;cursor:pointer;outline:none}
select:focus{border-color:#388bfd}
.sep{color:#30363d;font-size:16px;padding:0 2px}
.stats{margin-left:auto;font-size:12px;color:#8b949e;white-space:nowrap}
.stats b{color:#e6edf3}
main{display:flex;flex:1;overflow:hidden;min-height:0}
.pane{flex:1;overflow:auto;min-width:0;scroll-behavior:auto}
.pane+.pane{border-left:2px solid #30363d}
.pane-label{position:sticky;top:0;background:#161b22;border-bottom:1px solid #30363d;
  padding:5px 10px;font-size:11px;font-weight:600;color:#8b949e;z-index:1;
  white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
table{width:100%;border-collapse:collapse;
  font-family:"SFMono-Regular",Consolas,"Liberation Mono",Menlo,monospace;
  font-size:12px;line-height:1.45}
td{white-space:pre;vertical-align:top}
.ln{width:1%;min-width:38px;padding:0 8px;text-align:right;color:#484f58;
  user-select:none;border-right:1px solid #21262d;background:#0d1117}
.code{padding:0 10px}
.ph td{opacity:0;pointer-events:none;background:#161b22!important}
.ph .ln{background:#161b22!important}
tr.del{background:#2d1515}
tr.ins{background:#122412}
tr.chg{background:#1f2814}
tr.del .ln{background:#2d1515;color:#f85149}
tr.ins .ln{background:#122412;color:#3fb950}
tr.chg .ln{background:#1f2814;color:#d29922}
.del .code{color:#f85149}
.ins .code{color:#3fb950}
.chg .code{color:#e3b341}
.ws{opacity:0.45}
</style>
</head>
<body>
<header>
  <h1>Prince of Space</h1>
  <div class="controls">
    <label>Java</label>
    <select id="javaLevel">
      <option value="java8">Java 8</option>
      <option value="java17" selected>Java 17</option>
      <option value="java21">Java 21</option>
      <option value="java25">Java 25</option>
    </select>
    <span class="sep">|</span>
    <label>Left</label><select id="leftCfg"></select>
    <label>vs</label>
    <label>Right</label><select id="rightCfg"></select>
  </div>
  <div class="stats">Changed <b id="sChg">0</b> &nbsp; Left-only <b id="sDel">0</b> &nbsp; Right-only <b id="sIns">0</b></div>
</header>
<main>
  <div class="pane" id="leftPane">
    <div class="pane-label" id="leftLabel"></div>
    <table id="leftTable"></table>
  </div>
  <div class="pane" id="rightPane">
    <div class="pane-label" id="rightLabel"></div>
    <table id="rightTable"></table>
  </div>
</main>
<script>
const OUTPUTS=__OUTPUTS__;
const LABELS=__LABELS__;
const CONFIGS=__CONFIGS__;

// Populate selects
const lSel=document.getElementById('leftCfg');
const rSel=document.getElementById('rightCfg');
CONFIGS.forEach((c,i)=>{
  const mk=()=>{const o=document.createElement('option');o.value=c;o.textContent=LABELS[c];return o;};
  lSel.appendChild(mk()); rSel.appendChild(mk());
});
rSel.selectedIndex=2; // default: balanced-cont8 vs balanced-cont4 — shows indent difference

// LCS-based line diff. Returns [{type:'eq'|'del'|'ins', a?:number, b?:number}].
// O(N*M) — correct and fast for files up to ~1000 lines each.
function diff(A,B){
  const N=A.length,M=B.length;
  // Flat Uint16Array DP table: dp[i*(M+1)+j] = LCS length of A[0..i-1], B[0..j-1]
  const dp=new Uint16Array((N+1)*(M+1));
  for(let i=1;i<=N;i++){
    const ai=A[i-1];
    const base=i*(M+1);
    const prev=(i-1)*(M+1);
    for(let j=1;j<=M;j++){
      if(ai===B[j-1]) dp[base+j]=dp[prev+j-1]+1;
      else dp[base+j]=dp[prev+j]>dp[base+j-1]?dp[prev+j]:dp[base+j-1];
    }
  }
  // Backtrack
  const edits=[];
  let i=N,j=M;
  while(i>0||j>0){
    if(i>0&&j>0&&A[i-1]===B[j-1]){
      edits.push({type:'eq',a:i-1,b:j-1}); i--; j--;
    } else if(j>0&&(i===0||dp[i*(M+1)+j-1]>=dp[(i-1)*(M+1)+j])){
      edits.push({type:'ins',b:j-1}); j--;
    } else {
      edits.push({type:'del',a:i-1}); i--;
    }
  }
  edits.reverse();
  return edits;
}

// Build paired rows: [{leftLine, rightLine, cls}]
// Adjacent del+ins runs are zipped as 'chg'; leftovers padded with null
function pair(A,B,edits){
  const rows=[];
  let i=0;
  const flush=(dels,ins)=>{
    const n=Math.max(dels.length,ins.length);
    for(let j=0;j<n;j++){
      const l=j<dels.length?dels[j]:null;
      const r=j<ins.length?ins[j]:null;
      rows.push({l,r,cls:l&&r?'chg':l?'del':'ins'});
    }
  };
  let dels=[],ins=[];
  while(i<edits.length){
    const e=edits[i++];
    if(e.type==='eq'){
      flush(dels,ins); dels=[]; ins=[];
      rows.push({l:A[e.a],r:B[e.b],cls:'eq'});
    } else if(e.type==='del') dels.push(A[e.a]);
    else ins.push(B[e.b]);
  }
  flush(dels,ins);
  return rows;
}

function buildRow(text,ln,cls){
  const tr=document.createElement('tr');
  if(cls!=='eq') tr.className=cls;
  if(text===null){tr.className='ph';}
  const tn=document.createElement('td'); tn.className='ln'; tn.textContent=text!==null?ln:'';
  const tc=document.createElement('td'); tc.className='code';
  if(text===null){
    tc.textContent=' ';
  } else if(cls!=='eq'){
    // Render leading spaces as visible middle-dots so indent diffs are readable
    const m=text.match(/^( +)/);
    if(m){
      const ws=document.createElement('span'); ws.className='ws';
      ws.textContent='\u00b7'.repeat(m[1].length);
      tc.appendChild(ws);
      tc.appendChild(document.createTextNode(text.slice(m[1].length)));
    } else {
      tc.textContent=text;
    }
  } else {
    tc.textContent=text;
  }
  tr.appendChild(tn); tr.appendChild(tc);
  return tr;
}

function render(){
  const level=document.getElementById('javaLevel').value;
  const lKey=lSel.value, rKey=rSel.value;
  document.getElementById('leftLabel').textContent=LABELS[lKey];
  document.getElementById('rightLabel').textContent=LABELS[rKey];

  const lText=(OUTPUTS[level]&&OUTPUTS[level][lKey])||'';
  const rText=(OUTPUTS[level]&&OUTPUTS[level][rKey])||'';
  const A=lText.split('\n'), B=rText.split('\n');

  const edits=diff(A,B);
  const pairs=pair(A,B,edits);

  let nDel=0,nIns=0,nChg=0;
  pairs.forEach(p=>{if(p.cls==='del')nDel++;else if(p.cls==='ins')nIns++;else if(p.cls==='chg')nChg++;});
  document.getElementById('sChg').textContent=nChg;
  document.getElementById('sDel').textContent=nDel;
  document.getElementById('sIns').textContent=nIns;

  const lt=document.getElementById('leftTable');
  const rt=document.getElementById('rightTable');
  const lf=document.createDocumentFragment();
  const rf=document.createDocumentFragment();

  let ll=0,rl=0;
  pairs.forEach(p=>{
    const lln=p.l!==null?++ll:null;
    const rln=p.r!==null?++rl:null;
    lf.appendChild(buildRow(p.l,lln,p.cls));
    rf.appendChild(buildRow(p.r,rln,p.cls));
  });

  lt.innerHTML=''; rt.innerHTML='';
  lt.appendChild(lf); rt.appendChild(rf);
  document.getElementById('leftPane').scrollTop=0;
  document.getElementById('rightPane').scrollTop=0;
}

// Synchronized scrolling
let syncing=false;
['leftPane','rightPane'].forEach(id=>{
  const me=document.getElementById(id);
  const other=document.getElementById(id==='leftPane'?'rightPane':'leftPane');
  me.addEventListener('scroll',()=>{
    if(syncing) return;
    syncing=true;
    other.scrollTop=me.scrollTop;
    other.scrollLeft=me.scrollLeft;
    syncing=false;
  },{passive:true});
});

document.getElementById('javaLevel').addEventListener('change',render);
lSel.addEventListener('change',render);
rSel.addEventListener('change',render);
render();
</script>
</body>
</html>
"""


def generate(data: dict) -> str:
    outputs_json = json.dumps(data, ensure_ascii=False)
    labels_json = json.dumps(LABELS, ensure_ascii=False)
    configs_json = json.dumps(CONFIGS, ensure_ascii=False)
    return (
        HTML
        .replace("__OUTPUTS__", outputs_json)
        .replace("__LABELS__", labels_json)
        .replace("__CONFIGS__", configs_json)
    )


if __name__ == "__main__":
    data = load_outputs()
    html = generate(data)
    OUT_FILE.write_text(html, encoding="utf-8")
    size_kb = OUT_FILE.stat().st_size // 1024
    print(f"Generated {OUT_FILE.relative_to(REPO)} ({size_kb} KB)")
