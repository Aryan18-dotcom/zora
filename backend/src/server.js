import 'dotenv/config';
import express from 'express';
import helmet from 'helmet';
import cors from 'cors';
import morgan from 'morgan';
import mongoose from 'mongoose';
import { WebSocketServer } from 'ws';
import http from 'node:http';
import crypto from 'node:crypto';

const app=express(); const server=http.createServer(app);
app.use(helmet()); app.use(cors({origin:false})); app.use(express.json({limit:'1mb'})); app.use(morgan('combined'));
const callSchema=new mongoose.Schema({callId:{type:String,unique:true,index:true},callerNumber:String,callerName:{type:String,default:'Unknown caller'},startedAt:Date,endedAt:Date,durationSeconds:Number,status:{type:String,default:'in_progress'},transcript:{type:String,default:''},summary:{type:String,default:''},actionItems:{type:[String],default:[]},provider:String},{timestamps:true});
const Call=mongoose.model('Call',callSchema);
const settingsSchema=new mongoose.Schema({key:{type:String,unique:true},value:mongoose.Schema.Types.Mixed},{timestamps:true});
const Setting=mongoose.model('Setting',settingsSchema);
const requireAppToken=(req,res,next)=>{const got=(req.headers.authorization||'').replace(/^Bearer\s+/i,'');const expected=process.env.APP_TOKEN||'';const a=Buffer.from(got);const b=Buffer.from(expected);if(!expected||a.length!==b.length||!crypto.timingSafeEqual(a,b))return res.status(401).json({error:'UNAUTHORIZED'});next();};
const requireProvider=(req,res,next)=>{const expected=process.env.EXOTEL_WEBHOOK_TOKEN;if(!expected||req.get('x-zora-webhook-token')!==expected)return res.status(401).send('unauthorized');next();};
app.get('/health',(_req,res)=>res.json({ok:true,service:'zora-api',time:new Date().toISOString()}));
app.get('/api/v1/status',requireAppToken,async(_req,res)=>{const s=await Setting.findOne({key:'assistant'}).lean();res.json({enabled:Boolean(s?.value?.enabled),provider:s?.value?.provider||'exotel',configured:Boolean(process.env.MONGODB_URI)});});
app.put('/api/v1/assistant',requireAppToken,async(req,res)=>{const enabled=Boolean(req.body?.enabled);const s=await Setting.findOneAndUpdate({key:'assistant'},{$set:{value:{enabled,updatedAt:new Date()}}},{upsert:true,new:true});res.json({enabled:s.value.enabled,forwardingChanged:false,message:'App state saved. Carrier forwarding must be enabled/disabled through the confirmed Jio workflow.'});});
app.get('/api/v1/calls',requireAppToken,async(_req,res)=>{const calls=await Call.find().sort({startedAt:-1}).limit(100).lean();res.json(calls.map(c=>({...c,displayTime:c.startedAt?new Date(c.startedAt).toLocaleString('en-IN',{timeZone:'Asia/Kolkata'}):''})))});
app.get('/api/v1/calls/:id',requireAppToken,async(req,res)=>{const c=await Call.findOne({callId:req.params.id}).lean();if(!c)return res.status(404).json({error:'NOT_FOUND'});res.json(c)});
app.delete('/api/v1/calls/:id',requireAppToken,async(req,res)=>{await Call.deleteOne({callId:req.params.id});res.status(204).end()});
// Provider callbacks must be authenticated and idempotent. Map the provider's actual webhook fields here after account onboarding.
app.post('/provider/exotel/events',requireProvider,async(req,res)=>{const body=req.body||{};const callId=String(body.CallSid||body.call_sid||body.callId||'');if(!callId)return res.status(400).json({error:'MISSING_CALL_ID'});const event=String(body.Status||body.status||'').toLowerCase();const update={callerNumber:String(body.From||body.from||'Unknown'),provider:'exotel'};if(['completed','hangup','ended'].includes(event)){update.status='completed';update.endedAt=new Date();}else if(['in-progress','answered','active'].includes(event)){update.status='in_progress';update.startedAt=new Date();}await Call.findOneAndUpdate({callId},{$set:update,$setOnInsert:{callId}},{upsert:true,new:true});res.json({received:true});});
// Transcript ingestion is kept separate from telephony events; only the voice pipeline may call this route.
app.post('/provider/transcripts',requireProvider,async(req,res)=>{const {callId,callerName,transcript,summary,actionItems,durationSeconds}=req.body||{};if(!callId||typeof transcript!=='string')return res.status(400).json({error:'INVALID_TRANSCRIPT'});await Call.findOneAndUpdate({callId},{$set:{callerName:callerName||'Unknown caller',transcript,summary:summary||'',actionItems:Array.isArray(actionItems)?actionItems:[],durationSeconds:Number(durationSeconds)||0,status:'completed',endedAt:new Date()},$setOnInsert:{callId,startedAt:new Date(),callerNumber:'Unknown'}},{upsert:true,new:true});res.json({saved:true});});
app.use((err,_req,res,_next)=>{console.error(err);res.status(500).json({error:'INTERNAL_ERROR'});});
const wss=new WebSocketServer({noServer:true});
server.on('upgrade',(req,socket,head)=>{if(req.url!='/provider/exotel/stream'){socket.destroy();return;}wss.handleUpgrade(req,socket,head,ws=>wss.emit('connection',ws,req));});
wss.on('connection',(ws)=>{console.log('Exotel media socket connected.');ws.on('message',(raw)=>{let packet;try{packet=JSON.parse(raw.toString())}catch{return;} // Exotel media frames are base64 8 kHz mono PCM; connect this adapter to a tested STT/TTS engine before enabling live traffic.
 if(packet.event==='start') console.log('Voice stream started',packet.start?.call_sid||packet.start?.callSid||'');
 if(packet.event==='stop') console.log('Voice stream stopped');
 });ws.on('error',e=>console.error('voice websocket',e));});
const port=Number(process.env.PORT||10000);
if(process.env.MONGODB_URI){await mongoose.connect(process.env.MONGODB_URI);console.log('MongoDB connected');}else console.warn('MONGODB_URI missing; database routes will fail until configured.');
server.listen(port,'0.0.0.0',()=>console.log(`Zora API listening on ${port}`));
