!function a(){"use strict";function mb(a){var b=hb.get(1);if(b!==za&&b!==K)throw new vc("found accession outside of sequence or cross-references");var c={};c.value=null,c.source=ic(d,a.attributes),c.comment=ic(e,a.attributes),c.source||(c.source="?"),b===za?hc().accession=c:nc(c),ib.push(c)}function nb(a){var b=hb.get(1);if(b!=za)throw new vc("found annotation outside of sequence");var c={};c.evidence=ic(i,a.attributes),c.ref=ic(g,a.attributes),c.source=ic(h,a.attributes),c.type=ic(j,a.attributes),mc("annotations",c),ib.push(c)}function ob(){var a=hb.get(1);if(a!=l)throw new vc("found branch color outside of clade");var b={};b.red=0,b.green=0,b.blue=0,hc().color=b,ib.push(b)}function pb(a){var b={};if(b.branch_length=kc(m,a.attributes),b.collapse=lc(o,a.attributes),n in a.attributes&&(b.id_source=a.attributes[n]),null===fb){var c=ib.pop();if(!ib.isEmpty())throw new vc("severe phyloXML format error");fb=c,fb.children=[b]}else{var d=fc();void 0===d.children?d.children=[b]:d.children.push(b)}gb.push(b),ib.push(b)}function qb(a){var b={};b.distance=kc(s,a.attributes),b.id_ref_0=ic(t,a.attributes),b.id_ref_1=ic(u,a.attributes),b.type=ic(v,a.attributes),mc("clade_relations",b),ib.push(b)}function rb(a){var b={};b.distance=kc(x,a.attributes),b.id_ref_0=ic(y,a.attributes),b.id_ref_1=ic(z,a.attributes),b.type=ic(A,a.attributes),mc("sequence_relations",b),ib.push(b)}function sb(a){var b={};b.value=null,b.type=ic(H,a.attributes),b.stddev=kc(I,a.attributes);var c=hb.get(1);c===l||c===ca?mc(J,b):c!==f&&c!==V&&c!==r&&c!==w||(hc().confidence=b),ib.push(b)}function tb(){var a=hb.get(1);if(a!=za)throw new vc("found cross-reference outside of sequence");var b=[];hc().cross_references=b,ib.push(b)}function ub(a){var b={};b.unit=ic(M,a.attributes),hc().date=b,ib.push(b)}function vb(a){var b={};b.desc=null,b.unit=ic(M,a.attributes),mc("distributions",b),ib.push(b)}function wb(a){var b={};b.domains=null,b.length=jc(U,a.attributes),hc().domain_architecture=b,ib.push(b)}function xb(){var a={};hc().events=a,ib.push(a)}function yb(a){var b={};b.value=null,b.provider=ic(_,a.attributes),hc().id=b,ib.push(b)}function zb(a){var b={};b.is_aligned=lc(ba,a.attributes),hc().mol_seq=b,ib.push(b)}function Ab(a){var b={};b.alt_unit=ic(fa,a.attributes),b.geodetic_datum=ic(ga,a.attributes);var c=hb.get(1);c===R&&mc("points",b),ib.push(b)}function Bb(a){var b={};if(b.ref=ic(la,a.attributes),b.unit=ic(na,a.attributes),b.datatype=ic(oa,a.attributes),b.applies_to=ic(pa,a.attributes),b.id_ref=ic(ma,a.attributes),!b.ref)throw new vc("property ref is missing");if(!b.datatype)throw new vc("property data-type is missing");if(!b.applies_to)throw new vc("property applies-to is missing");if(!jb.test(b.ref))throw new vc("property ref is ill-formatted: "+b.ref);if(!lb.test(b.datatype))throw new vc("property data-type is ill-formatted: "+b.datatype);if(b.unit&&!kb.test(b.unit))throw new vc("property unit is ill-formatted: "+b.unit);mc(qa,b),ib.push(b)}function Cb(a){var b={};b.name=null,b.from=jc(sa,a.attributes),b.to=jc(ta,a.attributes),b.confidence=kc(ua,a.attributes),b.id=ic(va,a.attributes),mc("domains",b),ib.push(b)}function Db(a){var b={};b.doi=ic(xa,a.attributes),mc("references",b),ib.push(b)}function Eb(a){var b={};b.type=ic(Ca,a.attributes),b.id_source=ic(Aa,a.attributes),b.id_ref=ic(Ba,a.attributes),mc(Ha,b),ib.push(b)}function Fb(a){var b={};b.id_source=ic(Ja,a.attributes),mc(Qa,b),ib.push(b)}function Gb(a){var b={};b.value=null,b.desc=ic(Ua,a.attributes),b.type=ic(Ta,a.attributes),mc("uris",b),ib.push(b)}function Hb(a){var b={};b.rooted=lc(Va,a.attributes),void 0===b.rooted&&(b.rooted=!0),b.rerootable=lc(Wa,a.attributes),void 0===b.rerootable&&(b.rerootable=!0),b.branch_length_unit=ic(Xa,a.attributes),b.type=ic(Ya,a.attributes),ib.push(b)}function Ib(){var a={};hc().simple_characteristics=a,ib.push(a)}function Jb(a){hc().value=a}function Kb(a){gc()===k&&(hc().desc=a)}function Lb(a){gc()===C?hc().red=pc(a):gc()===D?hc().green=pc(a):gc()===E&&(hc().blue=pc(a)),gc()===F&&(hc().alpha=pc(a))}function Mb(a){gc()===p?fc().name=a:gc()===m?fc().branch_length=oc(a):gc()===q&&(fc().width=oc(a))}function Nb(a){hc().value=oc(a)}function Ob(a){gc()===N?hc().desc=a:gc()===O?hc().value=oc(a):gc()===P?hc().minimum=oc(a):gc()===Q&&(hc().maximum=oc(a))}function Pb(a){gc()===S&&(hc().desc=a)}function Qb(a){gc()===W?hc().type=a:gc()===X?hc().duplications=pc(a):gc()===Y?hc().speciations=pc(a):gc()===Z&&(hc().losses=pc(a))}function Rb(a){hc().value=a}function Sb(a){hc().value=a}function Tb(a){gc()===ha?hc().lat=a:gc()===ia?hc().long=a:gc()===ja&&(hc().alt=a)}function Ub(a){hc().value=a}function Vb(a){hc().name=a}function Wb(a){gc()===Za?hc().name=a:gc()===$a?hc().description=a:gc()===_a&&(hc().date=a)}function Xb(a){gc()===ya&&(hc().desc=a)}function Yb(a){gc()===Da?hc().symbol=a:gc()===Ea?hc().name=a:gc()===Fa?hc().gene_name=a:gc()===Ga&&(hc().location=a)}function Zb(a){gc()===Ka?hc().code=a:gc()===La?hc().scientific_name=a:gc()===Ma?hc().authority=a:gc()===Na?hc().common_name=a:gc()===Oa?mc(Ra,a):gc()===Pa&&(hc().rank=a)}function $b(a){hc().value=a}function _b(a){gc()===bb?hc().country=a:gc()===db?hc().host=a:gc()===cb&&(hc().year=a)}function ac(a){switch(hb.push(a.name),a.name){case l:pb(a);break;case c:mb(a);break;case f:nb(a);break;case r:hb.get(1)===ca&&qb(a);break;case B:ob();break;case G:sb(a);break;case K:tb();break;case L:hb.get(1)===l&&ub(a);break;case R:vb(a);break;case T:wb(a);break;case V:xb();break;case $:yb(a);break;case aa:zb(a);break;case ea:Ab(a);break;case ra:Cb(a);break;case ca:Hb(a);break;case ka:Bb(a);break;case wa:Db(a);break;case za:Eb(a);break;case w:hb.get(1)===ca&&rb(a);break;case Ia:Fb(a);break;case Sa:Gb(a);break;case ab:Ib()}}function bc(a){a===l?(hb.pop(),ib.pop(),gb.pop()):a===c||a===f||a===r&&hb.get(1)===ca||a===B||a===G||a===K||a===L&&hb.get(1)===l||a===R||a===Ia||a===$||a===V||a===aa||a===wa||a===T||a===ra||a===za||a===w&&hb.get(1)===ca||a===ka||a===ea||a===Sa||a===ab?(hb.pop(),ib.pop()):a!==ca&&a!==da?hb.pop():a===ca&&(sc(),eb.push(fb),rc())}function cc(a){var b=hb.get(1),d=hb.peek();b===l?Mb(a):b===f?Kb(a):b===B?Lb(a):b===L?Ob(a):b===R?Pb(a):b===V?Qb(a):b===wa?Xb(a):b===ca?Wb(a):b===ea?Tb(a):b===za?Yb(a):b===Ia&&Zb(a),d===c?Jb(a):d===G?Nb(a):d===$?Rb(a):d===aa?Sb(a):d===ra?Vb(a):d===ka?Ub(a):d===Sa?$b(a):b===ab&&_b(a)}function dc(a){throw console.error(a),a}function ec(a){a.onopentag=ac,a.onclosetag=bc,a.ontext=cc,a.onerror=dc}function fc(){return gb.peek()}function gc(){return hb.peek()}function hc(){return ib.peek()}function ic(a,b){if(a in b)return b[a]}function jc(a,b){if(a in b)return pc(b[a])}function kc(a,b){if(a in b)return oc(b[a])}function lc(a,b){if(a in b)return qc(b[a])}function mc(a,b){var c=null;c=hc()?hc():fb;var d=c[a];d?d.push(b):c[a]=[b]}function nc(a){var b=hc();b.push(a)}function oc(a){var b=parseFloat(a);if(isNaN(b))throw new vc("could not parse floating point number from '"+a+"'");return b}function pc(a){var b=parseInt(a);if(isNaN(b))throw new vc("could not parse integer number from '"+a+"'");return b}function qc(a){if("true"===a)return!0;if("false"===a)return!1;throw new vc("could not parse boolean from '"+a+"'")}function rc(){fb=null,gb=new uc,hb=new uc,ib=new uc}function sc(){if(!gb.isEmpty()||!ib.isEmpty())throw new vc("severe phyloXML format error")}function tc(){if(!hb.isEmpty())throw new vc("severe phyloXML format error")}function uc(){this._stack=[],this.pop=function(){var a=this._stack.pop();if(void 0===a)throw new Error("severe phyloXML format error");return a},this.push=function(a){this._stack.push(a)},this.peek=function(){return this._stack[this._stack.length-1]},this.get=function(a){return this._stack[this._stack.length-(1+a)]},this.length=function(){return this._stack.length},this.isEmpty=function(){return this._stack.length<1}}function vc(a){this.name="phyloXmlError",this.message=a||"phyloXML format error"}var b=null;if("undefined"!=typeof module&&module.exports&&!global.xmldocAssumeBrowser)b=require("./lib/sax");else if("undefined"!=typeof window){if(b=window.sax,!b)throw new Error("Expected sax to be defined. Make sure you are including sax.js before this file.")}else if(b=this.sax,!b)throw new Error("Expected sax to be defined. Make sure you are including sax.js before this file.");var c="accession",d="source",e="comment",f="annotation",g="ref",h="source",i="evidence",j="type",k="desc",l="clade",m="branch_length",n="id_source",o="collapse",p="name",q="width",r="clade_relation",s="distance",t="id_ref_0",u="id_ref_1",v="type",w="sequence_relation",x="distance",y="id_ref_0",z="id_ref_1",A="type",B="color",C="red",D="green",E="blue",F="alpha",G="confidence",H="type",I="stddev",J="confidences",K="cross_references",L="date",M="unit",N="desc",O="value",P="minimum",Q="maximum",R="distribution",S="desc",T="domain_architecture",U="length",V="events",W="type",X="duplications",Y="speciations",Z="losses",$="id",_="provider",aa="mol_seq",ba="is_aligned",ca="phylogeny",da="phyloxml",ea="point",fa="alt_unit",ga="geodetic_datum",ha="lat",ia="long",ja="alt",ka="property",la="ref",ma="id_ref",na="unit",oa="datatype",pa="applies_to",qa="properties",ra="domain",sa="from",ta="to",ua="confidence",va="id",wa="reference",xa="doi",ya="desc",za="sequence",Aa="id_source",Ba="id_ref",Ca="type",Da="symbol",Ea="name",Fa="gene_name",Ga="location",Ha="sequences",Ia="taxonomy",Ja="id_source",Ka="code",La="scientific_name",Ma="authority",Na="common_name",Oa="synonym",Pa="rank",Qa="taxonomies",Ra="synonyms",Sa="uri",Ta="type",Ua="desc",Va="rooted",Wa="rerootable",Xa="branch_length_unit",Ya="type",Za="name",$a="description",_a="date",ab="Simple_Characteristics",bb="Country",cb="Year",db="Host",eb=null,fb=null,gb=null,hb=null,ib=null,jb=/[a-zA-Z0-9_]+:\S+/,kb=/[a-zA-Z0-9_]+:\S+/,lb=/xsd:\S+/;vc.prototype=Object.create(Error.prototype),a.toPhyloXML_=function(a,b){function h(a){var f,g;if(l(a,[n,o]),i(p,a.name),a[m]&&i(m,b&&b>0?x(a[m],b):a[m]),a[J]&&a[J].length>0)for(f=a[J].length,g=0;g<f;++g){var s=a[J][g];s[H]||(s[H]="?"),i(G,s.value,s,[H,I])}if(i(q,a[q]),a[B]){var t=a[B];j(B),i(C,t[C]),i(D,t[D]),i(E,t[E]),i(F,t[F]),k(B)}if(a[Qa]&&a[Qa].length>0)for(f=a[Qa].length,g=0;g<f;++g){var u=a[Qa][g];if(j(Ia,u,[Ja]),u[$]&&(u[$][_]||(u[$][_]="?"),i($,u[$].value,u[$],[_])),i(Ka,u[Ka]),i(La,u[La]),i(Ma,u[Ma]),i(Na,u[Na]),u[Ra]&&u[Ra].length>0)for(var v=u[Ra].length,w=0;w<v;++w)i(Oa,u[Ra][w]);i(Pa,u[Pa]),k(Ia)}if(a[Ha]&&a[Ha].length>0)for(f=a[Ha].length,g=0;g<f;++g){var y=a[Ha][g];j(za,y,[Ca,Aa,Ba]),i(Da,y[Da]),y[c]&&(y[c][d]||(y[c][d]="?"),i(c,y[c].value,y[c],[d,e])),i(Ea,y[Ea]),i(Fa,y[Fa]),i(Ga,y[Ga]),y[aa]&&i(aa,y[aa].value,y[aa],[ba]),k(za)}if(a[V]){var z=a[V];if(j(V),i(W,z[W]),i(X,z[X]),i(Y,z[Y]),i(Z,z[Z]),z[G]){var A=z[G];i(G,A.value,A,[H,I])}k(V)}if(a[qa]&&a[qa].length>0)for(f=a[qa].length,g=0;g<f;++g){var K=a[qa][g];if(!K[pa])throw new vc("property applies-to is missing");if(!K[oa])throw new vc("property data-type is missing");if(!K[la])throw new vc("property ref is missing");i(ka,K.value,K,[la,na,oa,pa,ma])}if(a.children)for(f=a.children.length,g=0;g<f;++g)h(a.children[g]);else if(a._children)for(f=a._children.length,g=0;g<f;++g)h(a._children[g]);r()}function i(a,b,c,d){if(null!==b&&void 0!==b){if("string"==typeof b||b instanceof String){if(b=b.trim(),!(b.length>0))return;(b.indexOf("&")>-1||b.indexOf("<")>-1||b.indexOf(">")>-1||b.indexOf('"')>-1||b.indexOf("'")>-1)&&(b=y(b))}f+=g+"<"+a,c&&d&&d.length>0&&w(c,d),f+=">"+b+"</"+a+">\n"}}function j(a,b,c){b&&c&&c.length>0?(f+=g+"<"+a,w(b,c),f+=">\n"):f+=g+"<"+a+">\n",g+=" "}function k(a){z(),f+=g+"</"+a+">\n"}function l(a,b){a&&b&&b.length>0?(f+=g+"<clade",w(a,b),f+=">\n"):f+=g+"<clade>\n",g+=" "}function r(){z(),f+=g+"</clade>\n"}function s(a,b){void 0!==a[Va]&&null!==a[Va]||(a[Va]=!0),void 0!==a[Wa]&&null!==a[Wa]||(a[Wa]=!0),a&&b&&b.length>0?(f+=" <phylogeny",w(a,b),f+=">\n"):f+=" <phylogeny>\n",g="  "}function t(){g=" ",f+=" </phylogeny>\n"}function u(){g="",f+='<?xml version="1.0" encoding="UTF-8"?>\n',f+='<phyloxml xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://www.phyloxml.org http://www.phyloxml.org/1.20/phyloxml.xsd" xmlns="http://www.phyloxml.org">\n'}function v(){f+="</phyloxml>\n"}function w(a,b){for(var c=b.length,d=0;d<c;++d){var e=b[d];e&&void 0!==a[e]&&null!==a[e]&&(f+=" "+e+'="'+a[e]+'"')}}function x(a,b){return Math.round(a*Math.pow(10,b))/Math.pow(10,b)}function y(a){return a.replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;").replace(/"/g,"&quot;").replace(/'/g,"&apos;")}function z(){var a=g.length;g="";for(var b=0;b<=a-2;++b)g+=" "}var f="",g="";return u(),s(a,[Va,Wa,Xa,Ya]),a.children&&1===a.children.length&&h(a.children[0]),t(),v(),f},a.parseAsync=function(a,c){eb=[],rc();var d=b.createStream(!0,c);ec(d),a.pipe(d),d.on("end",function(){tc();var a=eb.length;console.log("parsed "+a+" trees")}),process.stdout.on("drain",function(){a.resume()})},a.parse=function(a,c){if(a&&(a=a.toString().trim()),!a)throw new Error("phyloXML source is empty");eb=[],rc();var d=b.parser(!0,c);return ec(d),d.onend=function(){tc()},d.write(a).close(),eb},a.toPhyloXML=function(b,c){return a.toPhyloXML_(b,c)},"undefined"!=typeof module&&module.exports&&!global.xmldocAssumeBrowser?module.exports.phyloXml=a:"undefined"!=typeof window?window.phyloXml=a:this.phyloXml=a}();