# Independent Python port of StyParser + StyleConverter logic, used to produce/verify expected test numbers.
import struct,sys,collections,json
sys.path.insert(0,'/home/claude/tools')
def vlq(d,i):
    v=0;n=0
    while True:
        b=d[i];i+=1;v=(v<<7)|(b&0x7f);n+=1
        if not b&0x80:return v,i
def read_midi(d):
    assert d[:4]==b'MThd'
    hl=struct.unpack('>I',d[4:8])[0];fmt,ntr,div=struct.unpack('>HHH',d[8:14]);i=8+hl
    tracks=[];extra=[]
    while i+8<=len(d):
        tag=d[i:i+4].decode('latin1');ln=struct.unpack('>I',d[i+4:i+8])[0];st=i+8;en=min(len(d),st+ln);i=en
        if tag=='MTrk': tracks.append(read_track(d,st,en))
        else: extra.append((tag,d[st:en]))
    return div,tracks,extra
def read_track(d,p,end):
    ev=[];metas=[];t=0;run=0
    while p<end:
        dt,p=vlq(d,p);t+=dt
        s=d[p]
        if s==0xFF:
            ty=d[p+1];l,p=vlq(d,p+2);metas.append((t,ty,d[p:p+l]));p+=l
            if ty==0x2F:break
        elif s in(0xF0,0xF7):
            l,p=vlq(d,p+1);p+=l
        else:
            if s&0x80: run=s;p+=1
            else: s=run
            n=1 if s&0xF0 in(0xC0,0xD0) else 2
            ev.append((t,s,d[p],d[p+1] if n==2 else 0));p+=n
    return ev,metas
NAMES=["Intro A","Intro B","Intro C","Intro D","Main A","Main B","Main C","Main D","Fill In AA","Fill In BB","Fill In CC","Fill In DD","Fill In BA","Ending A","Ending B","Ending C","Ending D"]
def casm(body):
    out={};i=0
    while i+8<=len(body):
        tag=body[i:i+4];ln=struct.unpack('>I',body[i+4:i+8])[0]
        if tag==b'CSEG':
            names=[];ents=[];j=i+8;end=i+8+ln
            while j+8<=end:
                t2=body[j:j+4];l2=struct.unpack('>I',body[j+4:j+8])[0];b=body[j+8:j+8+l2]
                if t2==b'Sdec': names=[x.strip() for x in b.decode('latin1').split(',')]
                if t2==b'Ctb2' and len(b)>=29 and 8<=b[9]<=15:
                    a=b[23]
                    ents.append(dict(src=b[0]&15,dest=b[9],ntt=0 if b[9]<=9 else a&0x7f,bass=bool(a&0x80),hk=b[24],lo=b[25],hi=b[26],rtr=b[27],root=b[18]))
                j+=8+l2
            for n in names: out.setdefault(n,[]).extend(ents)
        i+=8+ln
    return out
def parse(path):
    d=open(path,'rb').read()
    div,tracks,extra=read_midi(d)
    ev,metas=tracks[0]
    marks=sorted([(t,m.decode('latin1').strip('\0 ')) for t,ty,m in metas if ty==6 and m.decode('latin1').strip('\0 ') in NAMES])
    tempo=[int.from_bytes(m,'big') for t,ty,m in metas if ty==0x51][0]
    ts=[m for t,ty,m in metas if ty==0x58][0];num=ts[0];den=1<<ts[1]
    tpb=div*4*num//den
    last=max(e[0] for e in ev)
    open_={};raws=[]
    for t,s,a,b in ev:
        ch=s&15;ty=s&0xF0
        if ty==0x90 and b>0: open_.setdefault(ch*128+a,[]).append((t,b))
        elif ty==0x80 or (ty==0x90 and b==0):
            q=open_.get(ch*128+a)
            if q: o=q.pop(0);raws.append((o[0],max(1,t-o[0]),ch,a,o[1]))
    for k,q in open_.items():
        for o in q: raws.append((o[0],div//4,k//128,k%128,o[1]))
    cs={}
    for tag,body in extra:
        if tag=='CASM': cs=casm(body)
    secs=[]
    for k,(t,n) in enumerate(marks):
        end=marks[k+1][0] if k+1<len(marks) else max(last,t+tpb)
        ents=cs.get(n,[]);allowed={e['src'] for e in ents}
        notes=[r for r in raws if t<=r[0]<end and r[2] in allowed]
        bars=max(1,min(64,round((end-t)/tpb)))
        secs.append(dict(name=n,bars=bars,notes=len(notes),dests=sorted({e['dest'] for e in ents}),ntt={e['dest']:e['ntt'] for e in ents}))
    return dict(tempo=60000000//tempo,num=num,den=den,div=div,secs=secs)
if __name__=='__main__':
    import glob
    res={}
    for p in sorted(glob.glob('/mnt/user-data/uploads/*.STY')):
        r=parse(p);res[p.split('/')[-1]]=r
        print(p.split('/')[-1],r['tempo'],r['num'],r['den'])
        for s in r['secs']: print('   ',s['name'],s['bars'],s['notes'],s['dests'])
    json.dump(res,open('/home/claude/tools/expected.json','w'),indent=1)
