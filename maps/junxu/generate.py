"""Reproducible 1.8.8 fortress: validated inert blueprint plus explicit legacy details.

Run with --revision r1 (a new revision requires an empty output directory).
The small runtime template uses the same final block arrays as the schematic.
"""
from pathlib import Path
import argparse, gzip, hashlib, importlib.util, json, math, struct, sys

ROOT = Path(__file__).resolve().parent
TOOL = Path('/mnt/c/Users/kirom/.codex/skills/remiaft-map-creator/scripts/maptool.py')
spec = importlib.util.spec_from_file_location('maptool', TOOL)
maptool = importlib.util.module_from_spec(spec)
sys.modules['maptool'] = maptool
spec.loader.exec_module(maptool)
W, H, L = 61, 51, 61
palette = {
    'air': (0,0), 'stone': (1,0), 'andesite': (1,5), 'cobble': (4,0), 'moss': (48,0),
    'brick': (98,0), 'cracked': (98,2), 'carved': (98,3), 'wood': (5,5),
    'oak': (5,1), 'beam': (17,13), 'iron': (42,0), 'gold': (41,0),
    'lamp': (89,0), 'blue': (95,11), 'violet': (95,10), 'glass': (95,0),
    'black': (173,0), 'books': (47,0), 'red': (35,14), 'banner': (35,11),
    'pale': (159,4), 'sand': (24,2), 'earth': (159,12), 'grass': (159,13),
}
ops, details, signs = [], [], []
def box(a,b,mat):
    ops.append({'kind':'cuboid','from':list(a),'to':list(b),'block':mat})
def ellipsoid(c,r,mat):
    ops.append({'kind':'ellipsoid','center':list(c),'radii':list(r),'block':mat})
def point(x,y,z,mat): box((x,y,z),(x,y,z),mat)
def detail(x,y,z,block,data=0): details.append((x,y,z,block,data))
def stair(x,y,z,data=0): detail(x,y,z,109,data)
def sign(x,y,z,lines,rotation=8):
    detail(x,y,z,63,rotation); signs.append({'x':x,'y':y,'z':z,'lines':lines})
def lantern(x,y,z):
    box((x,y+1,z),(x,y+2,z),'iron'); point(x,y,z,'lamp')
    for dx,dz in [(-1,0),(1,0),(0,-1),(0,1)]: detail(x+dx,y,z+dz,101)
    detail(x,y-1,z,44,13)
def crate(x,y,z):
    point(x,y,z,'oak'); detail(x,y+1,z,96,12)
    for dx in [-1,1]: detail(x+dx,y,z,96,4 if dx<0 else 5)
def arch_x(x,z1,z2,y=23,h=8):
    for z in [z1,z2]: box((x,y,z),(x,y+h,z),'brick')
    box((x,y+h,z1),(x,y+h,z2),'beam')
    for step in range(3):
        point(x,y+h-step-1,z1+step,'brick'); point(x,y+h-step-1,z2-step,'brick')
def arch_z(z,x1,x2,y=23,h=8):
    for x in [x1,x2]: box((x,y,z),(x,y+h,z),'brick')
    box((x1,y+h,z),(x2,y+h,z),'beam')
    for step in range(3):
        point(x1+step,y+h-step-1,z,'brick'); point(x2-step,y+h-step-1,z,'brick')

# Layered, irregular-looking but deterministic floating rock with a tapered keel.
ellipsoid((30,17,30),(29,6,29),'stone')
ellipsoid((30,12,30),(22,7,22),'andesite')
ellipsoid((30,8,30),(15,6,16),'stone')
ellipsoid((30,4,30),(7,4,9),'black')
for x,z,r in [(12,20,7),(45,18,6),(18,44,7),(42,46,6),(8,32,5),(50,32,5)]:
    ellipsoid((x,15,z),(r,6,r),'cobble')
for x,z in [(19,24),(38,16),(25,42),(44,35)]:
    ellipsoid((x,10,z),(3,4,3),'moss')
for y in range(3,20,3):
    point(30,y,30,'violet'); point(31,y,30,'lamp')
box((3,20,3),(57,21,57),'brick')
for edge in [2,58]:
    box((edge,21,3),(edge,22,57),'carved'); box((3,21,edge),(57,22,edge),'carved')
box((3,22,3),(57,22,57),'brick')
# Supporting ribs, diagonal buttress silhouettes and gold tension collars.
for x,z,dx,dz in [(4,4,1,1),(56,4,-1,1),(4,56,1,-1),(56,56,-1,-1)]:
    for n in range(15): box((x+dx*n,20-n,z+dz*n),(x+dx*n+1,22-n,z+dz*n+1),'brick')
    box((x,10,z),(x+1,21,z+1),'iron'); box((x-1,17,z-1),(x+2,18,z+2),'carved')
for x,z in [(5,5),(55,5),(5,55),(55,55)]:
    box((x-3,19,z-3),(x+3,24,z+3),'brick')
    box((x-2,25,z-2),(x+2,35,z+2),'brick')
    box((x-1,25,z-1),(x+1,34,z+1),'air')
    box((x-3,35,z-3),(x+3,36,z+3),'carved')
    for dx,dz in [(-3,-3),(-3,3),(3,-3),(3,3)]:
        box((x+dx,36,z+dz),(x+dx,38,z+dz),'brick')
    box((x-1,37,z-1),(x+1,37,z+1),'wood'); lantern(x,39,z)
    for dx,dz in [(-2,0),(2,0),(0,-2),(0,2)]:
        box((x+dx,27,z+dz),(x+dx,31,z+dz),'blue')
    box((x,41,z),(x,47,z),'iron')
    box((x+1,44,z),(x+4,47,z),'banner')
    box((x+2,45,z),(x+2,46,z),'gold')
    point(x+3,45,z,'gold')

# Main floor and four broad paths.
box((8,22,8),(52,22,32),'wood')
box((24,22,7),(36,22,34),'sand')
box((8,22,16),(52,22,20),'sand')
box((10,22,29),(50,22,33),'sand')
for z in range(9,33,4): point(30,22,z,'carved')
for x in [23,37]: box((x,22,8),(x,22,32),'gold')

# Tall external walls, masonry plinths, dark framing and deep windows.
for x in [7,53]:
    box((x,23,8),(x,30,32),'brick')
    for z in [10,17,24,30]:
        box((x,23,z-1),(x,31,z+1),'beam')
    for z in [13,20,27]:
        box((x,25,z-1),(x,29,z+1),'glass')
        box((x,30,z-1),(x,30,z+1),'carved')
        for n in [-1,0,1]: detail(x,24,z+n,44,5)
for z in [8,32]:
    box((8,23,z),(52,30,z),'brick')
    for x in [10,18,24,36,42,50]: box((x,23,z),(x,31,z),'beam')
    for x in [14,46]: box((x-2,25,z),(x+2,29,z),'glass')
    box((27,23,z),(33,29,z),'air'); arch_z(z,26,34)
# Internal room partitions preserve 5-wide cross corridors.
for x in [22,38]:
    box((x,23,9),(x,30,31),'wood')
    for za,zb in [(15,21),(27,31)]:
        box((x,23,za),(x,29,zb),'air'); arch_x(x,za-1,zb+1)
for x in [9,21,39,51]:
    for z in [9,28]:
        box((x,23,z),(x,31,z),'beam'); point(x,25,z,'gold')

# Ceiling coffers, longitudinal beams, stepped pitched roofs and skylight spine.
box((7,31,8),(53,31,32),'wood')
for x in range(8,54,6): box((x,30,8),(x,30,32),'beam')
for z in [10,18,26,32]: box((7,30,z),(53,30,z),'beam')
for x in [14,46]:
    for z in [13,25]: lantern(x,28,z)
for z in [12,24]: lantern(30,30,z)
for n in range(6):
    box((6+n,32+n,7),(22-n,32+n,33),'wood')
    box((38+n,32+n,7),(54-n,32+n,33),'wood')
box((23,35,8),(37,35,32),'brick')
box((25,36,10),(35,36,30),'blue')
box((28,37,13),(32,37,27),'glass')
for x in [24,36]: box((x,32,8),(x,36,32),'beam')
box((25,31,10),(35,34,30),'air')
for z in [10,16,22,28,32]: box((24,35,z),(36,35,z),'beam')

# Partial second-storey galleries in the two wings and accessible staircases.
for xa,xb in [(9,20),(40,51)]:
    box((xa,31,9),(xb,31,12),'wood')
    box((xa,32,9),(xb,35,12),'air')
    for x in range(xa,xb+1): detail(x,32,13,85)
    box((xa,33,8),(xb,34,8),'glass')
for x in [9,49]:
    for n in range(9):
        box((x,23,23-n),(x+2,23+n,23-n),'wood')
        box((x,24+n,23-n),(x+2,29+n,23-n),'air')
        for dx in range(3): detail(x+dx,23+n,23-n,134,3)
    box((x,31,12),(x+2,31,15),'wood')
    box((x,32,12),(x+2,35,15),'air')

# Portal frontage: a projecting portico with two columns, arch and deep lintels.
for x in [24,36]:
    box((x-1,23,3),(x+1,24,5),'carved')
    box((x,25,4),(x,30,4),'beam'); point(x,26,4,'gold')
arch_z(4,24,36,23,9)
box((23,32,3),(37,33,8),'brick')
for n in range(4): box((25+n,34+n,4),(35-n,34+n,7),'wood')
box((29,33,3),(31,35,3),'gold'); point(30,34,2,'blue')
for x in [20,40]:
    box((x,26,6),(x,32,6),'iron')
    box((x-2,27,6),(x+1,31,6),'banner')
    box((x,28,5),(x,30,5),'gold')
box((27,22,3),(33,22,11),'red')
sign(30,34,3,['§6第七军需处','§7余烬之约','','§b星火仍在'],8)
sign(27,23,6,['§6接待大厅','§f契约核心','§7前行进入',''],0)

# Contract core and engraved radial floor in the spacious central hall.
for dx in range(-5,6):
    for dz in range(-5,6):
        d=abs(dx)+abs(dz)
        if d in [4,5]: point(30+dx,22,23+dz,'gold' if d==4 else 'carved')
box((29,23,22),(31,23,24),'carved')
point(30,24,23,'gold'); point(30,25,23,'lamp'); point(30,26,23,'blue')
for dx,dz in [(-2,0),(2,0),(0,-2),(0,2)]:
    point(30+dx,23,23+dz,'iron'); detail(30+dx,24,23+dz,101)
sign(30,23,19,['§6星火核心','§f重燃军印','§7右键触碰核心',''],8)

# Rune workshop: reading bays, workbenches and tools, separate from its main route.
for z in [10,26]:
    box((13,23,z),(20,26,z),'books'); box((13,27,z),(20,27,z),'beam')
for x in [14,18]:
    box((x,23,13),(x+1,23,14),'wood'); detail(x,24,13,116)
    point(x+1,24,14,'blue'); detail(x+1,25,14,44,13)
    detail(x,23,15,134,2)
box((16,23,23),(20,23,24),'wood')
for x in [16,18,20]: detail(x,24,24,145,0)
lantern(18,28,18)
sign(20,23,17,['§d符文工坊','§f符文师·伊瑟','§7附魔与符文',''],12)
for x,z in [(13,28),(16,28),(19,28)]: crate(x,23,z)

# Tactical room: a raised relief map, briefing seats and wall maps in coloured clay.
box((41,23,23),(47,23,27),'wood')
box((41,24,23),(47,24,27),'sand')
box((42,25,24),(43,25,26),'grass'); point(44,25,25,'blue')
box((45,25,23),(46,26,24),'stone'); point(47,25,26,'red')
for x in [42,46]: detail(x,23,21,134,2)
for x in range(41,49):
    point(x,26,10,'grass' if x%3 else 'earth')
    point(x,27,10,'sand' if x%3 else 'blue')
box((43,23,13),(47,23,14),'wood')
for x in [43,45,47]: detail(x,23,15,134,2)
sign(40,23,17,['§b战术室','§f战术导师·凯恩','§7天赋与路线',''],4)
for x,z in [(42,28),(46,28)]: crate(x,23,z)

# Rear armoury hall and blade displays. The 7-wide doorway is the safety boundary.
for x in [13,17,21,39,43,47]:
    point(x,23,30,'carved'); box((x,24,31),(x,27,31),'iron')
    point(x-1,25,31,'wood'); point(x+1,25,31,'wood'); point(x,28,31,'gold')
for x in range(27,34): point(x,22,33,'gold')
sign(26,23,31,['§6兵器陈列廊','§f兵器大师·雷铎','§7精通与传承',''],8)
sign(34,23,33,['§c演武场边界','§7跨线进入战斗区','§f服务区禁止战斗',''],8)

# Twenty-seven by twenty-three training surface; no obstacles inside patrol lanes.
box((17,22,34),(43,22,56),'sand')
for x in [17,43]: box((x,22,34),(x,22,56),'wood')
for z in [34,56]: box((17,22,z),(43,22,z),'wood')
for x in [21,39]: box((x,22,37),(x,22,53),'brick')
for z in [37,53]: box((21,22,z),(39,22,z),'brick')
for i in range(10): point(20+(i%5)*5,22,42+(i//5)*8,'carved')
for x in [15,45]:
    box((x,22,34),(x,22,57),'brick')
    for z in [34,42,50,57]:
        box((x,23,z),(x,30,z),'beam'); point(x,25,z,'gold')
    for za,zb in [(34,42),(42,50),(50,57)]: arch_x(x,za,zb,23,8)
    for z in range(35,57): detail(x,23,z,101)
for x in range(16,45): detail(x,23,58,101)
arch_z(58,15,45,23,8)
for x in [21,30,39]: lantern(x,28,58)
# Low supply terraces beside the arena, and an unbroken outer railing.
for xa in [9,47]:
    for z in [37,45,51]:
        for dx in [0,3]: crate(xa+dx,23,z)
        point(xa+1,23,z+2,'carved'); detail(xa+1,24,z+2,145,0)
for n in range(9,53):
    for x,z in [(n,3),(n,57),(3,n),(57,n)]:
        if z==3 and 24<=x<=36: continue
        detail(x,23,z,101)
for x in [11,49]:
    for z in [35,43,53]: lantern(x,27,z)

markers=[
 {'id':'arrival','role':'spawn','pos':[30,23,11],'yaw':0},
 {'id':'exit','role':'exit','pos':[30,23,5],'yaw':180},
 {'id':'enchant','role':'npc','pos':[18,23,18],'yaw':270},
 {'id':'talent','role':'npc','pos':[42,23,18],'yaw':90},
 {'id':'mastery','role':'npc','pos':[24,23,30],'yaw':270},
 {'id':'core','role':'objective','pos':[30,24,23]},
 {'id':'training_min','role':'boundary','pos':[17,23,34]},
 {'id':'training_max','role':'boundary','pos':[43,30,56]},
]
for i in range(10): markers.append({'id':f'projection_{i+1:02}','role':'player_spawn','pos':[20+(i%5)*5,23,42+(i//5)*8],'yaw':180})
doc={'schema_version':1,'id':'seventh-quartermaster','title':'第七军需处·余烬之约','target':'1.8.8',
 'gameplay':'generic','size':[W,H,L],'palette':{k:{'id':v[0],'data':v[1]} for k,v in palette.items()},
 'operations':ops,'markers':markers,'metadata':{'theme':'悬浮于虚空裂隙中的远征军补给堡垒',
 'coordinates':'minimum corner; world base y=48; x/z separated by 512 blocks',
 'primary_floor_y':22,'clear_height':8,'arena_size':[27,23],'geometry_seed':'deterministic; no random source',
 'legacy_details':'generate.py adds verified 1.8 stairs, iron bars, signs, crates, anvils and enchantment tables after inert validation'}}

def nbtstr(s):
 b=s.encode();return struct.pack('>H',len(b))+b
def tag(t,n,p): return bytes([t])+nbtstr(n)+p
def inttag(n,v): return tag(3,n,struct.pack('>i',v))
def stringtag(n,v): return tag(8,n,nbtstr(v))
def final_schematic(blocks,data):
 tile=[]
 for s in signs:
  b=stringtag('id','Sign')+b''.join(inttag(k,s[k]) for k in ['x','y','z'])
  b+=b''.join(stringtag('Text'+str(i+1),json.dumps({'text':v},ensure_ascii=False)) for i,v in enumerate(s['lines']))
  tile.append(b+b'\0')
 root=b''.join(tag(2,n,struct.pack('>h',v)) for n,v in [('Width',W),('Height',H),('Length',L)])
 root+=stringtag('Materials','Alpha')
 for k in ['WEOriginX','WEOriginY','WEOriginZ','WEOffsetX','WEOffsetY','WEOffsetZ']:root+=inttag(k,0)
 root+=tag(7,'Blocks',struct.pack('>i',len(blocks))+bytes(blocks))+tag(7,'Data',struct.pack('>i',len(data))+bytes(data))
 root+=tag(9,'Entities',b'\x0a'+struct.pack('>i',0))
 root+=tag(9,'TileEntities',b'\x0a'+struct.pack('>i',len(tile))+b''.join(tile))
 return gzip.compress(tag(10,'Schematic',root+b'\0'),mtime=0)

def main():
 parser=argparse.ArgumentParser();parser.add_argument('--revision',required=True);args=parser.parse_args()
 out=ROOT/args.revision
 if out.exists() and any(out.iterdir()):raise SystemExit('Revision output must be empty')
 out.mkdir(parents=True,exist_ok=True)
 source=out/'project.json';source.write_text(json.dumps(doc,ensure_ascii=False,indent=2)+'\n')
 compiled=maptool.compile_blueprint(doc)
 maptool.build(compiled,out/'structure-v1',source)
 blocks=bytearray(compiled.blocks); data=bytearray(compiled.data)
 for x,y,z,b,d in details:
  assert 0<=x<W and 0<=y<H and 0<=z<L,(x,y,z)
  blocks[(y*L+z)*W+x]=b;data[(y*L+z)*W+x]=d
 for mark in markers:
  if mark['role'] in ['spawn','player_spawn','npc','exit']:
   x,y,z=mark['pos'];i=(y*L+z)*W+x
   assert blocks[i]==blocks[i+W*L]==0,mark
   assert blocks[i-W*L]!=0,mark
 # Flood-fill the 2-block-tall main layer, verifying every destination from arrival.
 start=(30,11);seen={start};todo=[start]
 for x,z in todo:
  for a,b in [(x+1,z),(x-1,z),(x,z+1),(x,z-1)]:
   if not(0<=a<W and 0<=b<L) or (a,b) in seen:continue
   i=(23*L+b)*W+a
   if blocks[i]==blocks[i+W*L]==0 and blocks[i-W*L]!=0:
    seen.add((a,b));todo.append((a,b))
 for mark in markers:
  if mark['role'] in ['spawn','player_spawn','npc','exit']:assert (mark['pos'][0],mark['pos'][2]) in seen,mark
 schematic=final_schematic(blocks,data);(out/'map.schematic').write_bytes(schematic)
 (out/'details.json').write_text(json.dumps({'blocks':details,'signs':signs},ensure_ascii=False,indent=2)+'\n')
 manifest={'revision':args.revision,'size':[W,H,L],'mainFloor':22,'worldBaseY':48,'arena':[17,34,43,56],
 'sha256':hashlib.sha256(schematic).hexdigest(),'nonAir':sum(b!=0 for b in blocks),'markers':markers,
 'signs':signs,'walkableMainFloor':len(seen),'checks':['marker headroom','all NPC and Bot spawns reachable','legacy material IDs'],
 'runtimeIntegration':'not_verified'}
 (out/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
 # Direct runtime format: dimensions, packed block/data pairs, then UTF-8 sign text.
 raw=struct.pack('>4i',0x4a585431,W,H,L)+bytes(blocks)+bytes(data)+struct.pack('>i',len(signs))
 for s in signs:
  raw+=struct.pack('>3i',s['x'],s['y'],s['z'])
  for line in s['lines']:raw+=nbtstr(line)
 (out/'template.bin.gz').write_bytes(gzip.compress(raw,mtime=0))
 # Preview data is geometry, never an invented render of the design.
 (out/'voxels.json').write_text(json.dumps({'size':[W,H,L],'blocks':list(blocks),'data':list(data)},separators=(',',':')))
 print(json.dumps({k:v for k,v in manifest.items() if k not in ['markers','signs']},ensure_ascii=False))

if __name__=='__main__':main()
