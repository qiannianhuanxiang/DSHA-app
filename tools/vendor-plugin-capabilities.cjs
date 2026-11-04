// Vendor only the pinned parser, never any plugin code.
const fs=require('node:fs'), path=require('node:path'), crypto=require('node:crypto');
const root=path.resolve(__dirname,'..'), pkgRoot=path.join(__dirname,'plugin-capabilities/node_modules/acorn');
const pkg=JSON.parse(fs.readFileSync(path.join(pkgRoot,'package.json')));
if(pkg.name!=='acorn'||pkg.version!=='8.15.0') throw Error('ACORN_PIN');
const source=fs.readFileSync(path.join(pkgRoot,'dist/acorn.js')), license=fs.readFileSync(path.join(pkgRoot,'LICENSE'));
fs.writeFileSync(path.join(root,'app/src/main/assets/plugin-acorn.cjs'),source);
fs.writeFileSync(path.join(root,'app/src/main/assets/plugin-acorn.LICENSE'),license);
const lock=JSON.parse(fs.readFileSync(path.join(__dirname,'plugin-capabilities/package-lock.json')));
fs.writeFileSync(path.join(__dirname,'plugin-capabilities/parser-lock.json'),JSON.stringify({name:pkg.name,version:pkg.version,
  integrity:lock.packages['node_modules/acorn'].integrity,sourceSha256:crypto.createHash('sha256').update(source).digest('hex'),
  licenseSha256:crypto.createHash('sha256').update(license).digest('hex')},null,2)+'\n');
