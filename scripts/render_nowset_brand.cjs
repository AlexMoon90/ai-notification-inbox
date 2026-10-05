// NODE_PATH must point to a runtime with sharp. No source board pixels are used.
const fs=require('fs'),sharp=require('sharp');
(async()=>{
 for(const f of fs.readdirSync('assets/branding').filter(x=>x.endsWith('.svg'))){
  await sharp('assets/branding/'+f,{density:216}).png().toFile('assets/branding/'+f.replace('.svg','.png'));
 }
 for(const name of ['nowset_logo_stacked','nowset_logo_stacked_light'])await sharp('assets/branding/'+name+'.svg',{density:216}).png().toFile('app/src/main/res/drawable-nodpi/'+name+'.png');
 await sharp('assets/branding/nowset_app_icon.svg').resize(512,512).flatten({background:'#0B1F44'}).png().toFile('assets/branding/nowset_app_icon_512.png');
})();
