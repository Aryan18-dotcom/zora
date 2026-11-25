package com.aryan.zora

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private val Midnight = Color(0xFF080B12)
private val Panel = Color(0xFF121925)
private val Panel2 = Color(0xFF182231)
private val Mint = Color(0xFF65E6B1)
private val Muted = Color(0xFF91A0B4)
private data class Note(val id:String,val name:String,val number:String,val summary:String,val transcript:String,val time:String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { ZoraApp(this) } }
}

@Composable private fun ZoraApp(context: Context) {
    val prefs = remember { context.getSharedPreferences("zora", Context.MODE_PRIVATE) }
    var baseUrl by remember { mutableStateOf(prefs.getString("base_url", "") ?: "") }
    var token by remember { mutableStateOf(prefs.getString("app_token", "") ?: "") }
    var agentOn by remember { mutableStateOf(prefs.getBoolean("agent_on", false)) }
    var selected by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("Connect your Zora backend") }
    var notes by remember { mutableStateOf(listOf<Note>()) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val savePrefs = { prefs.edit().putString("base_url",baseUrl.trim()).putString("app_token",token.trim()).apply() }
    val refresh = {
        scope.launch {
            busy=true
            try { notes=fetchNotes(baseUrl,token); status="Connected · ${notes.size} saved calls" }
            catch(e:Exception){ status="Connection failed: ${e.message ?: "check settings"}" }
            busy=false
        }
    }
    MaterialTheme(colorScheme = darkColorScheme(primary=Mint,background=Midnight,surface=Panel,onSurface=Color(0xFFF3F6FA),onBackground=Color(0xFFF3F6FA))) {
        Scaffold(containerColor=Midnight,bottomBar={ NavigationBar(containerColor=Panel) {
            NavigationBarItem(selected==0,{selected=0},icon={Text("◉")},label={Text("Home")})
            NavigationBarItem(selected==1,{selected=1},icon={Text("≡")},label={Text("History")})
            NavigationBarItem(selected==2,{selected=2},icon={Text("⚙")},label={Text("Settings")})
        }}) { pad ->
            Column(Modifier.fillMaxSize().padding(pad).padding(horizontal=20.dp).padding(top=18.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("ZORA",fontSize=27.sp,fontWeight=FontWeight.Black,letterSpacing=3.sp); Text("PERSONAL VOICE RECEPTIONIST",fontSize=10.sp,color=Muted,letterSpacing=1.5.sp) }
                    Box(Modifier.size(42.dp).background(Panel2,CircleShape),contentAlignment=Alignment.Center){Text("Z",color=Mint,fontWeight=FontWeight.Bold,fontSize=21.sp)}
                }
                Spacer(Modifier.height(22.dp))
                when(selected) {
                    0 -> {
                        Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(24.dp),modifier=Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                                Text(if(agentOn) "ASSISTANT ENABLED" else "ASSISTANT STANDBY",color=if(agentOn)Mint else Muted,fontSize=11.sp,fontWeight=FontWeight.Bold,letterSpacing=1.4.sp)
                                Spacer(Modifier.height(16.dp))
                                Box(Modifier.size(142.dp).border(1.dp,if(agentOn)Mint else Color(0xFF344154),CircleShape).padding(10.dp).background(Panel2,CircleShape).clickable {
                                    if(baseUrl.isBlank()||token.isBlank()) status="Add backend URL and app token in Settings first"
                                    else scope.launch {
                                        val next = !agentOn
                                        try { updateAssistant(baseUrl,token,next); agentOn=next; prefs.edit().putBoolean("agent_on",next).apply(); status=if(next) "Mode armed. Complete Jio forwarding manually only after provider testing." else "Mode disarmed. Disable Jio forwarding in MyJio as well." }
                                        catch(e:Exception){ status="Could not update backend: ${e.message ?: "connection error"}" }
                                    }
                                },contentAlignment=Alignment.Center){ Text(if(agentOn) "ARMED" else "OFF",fontSize=if(agentOn)22.sp else 32.sp,fontWeight=FontWeight.Black,color=if(agentOn)Mint else Color.White) }
                                Spacer(Modifier.height(14.dp))
                                Text(if(agentOn) "Zora is marked active" else "Tap to prepare Zora",fontSize=17.sp,fontWeight=FontWeight.SemiBold)
                                Spacer(Modifier.height(6.dp))
                                Text("The switch records your intended state. Carrier forwarding is not changed automatically in this alpha build.",fontSize=12.sp,color=Muted,lineHeight=18.sp)
                                Spacer(Modifier.height(16.dp))
                                Text(status,fontSize=12.sp,color=Muted)
                                OutlinedButton(onClick={refresh()},enabled=!busy,modifier=Modifier.fillMaxWidth()) { Text(if(busy) "Checking…" else "SYNC CALL HISTORY") }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            StatCard("${notes.size}","CALL NOTES",Modifier.weight(1f))
                            StatCard("JIO","NETWORK",Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(14.dp))
                        Text("RECENT ACTIVITY",fontSize=11.sp,color=Muted,fontWeight=FontWeight.Bold,letterSpacing=1.3.sp)
                        Spacer(Modifier.height(8.dp))
                        if(notes.isEmpty()) EmptyState("No calls recorded yet","Your completed assistant calls will appear here.")
                        else LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){items(notes.take(3)){NoteRow(it)}}
                    }
                    1 -> {
                        Text("Call history",fontSize=24.sp,fontWeight=FontWeight.Bold)
                        Text("Transcripts and caller summaries",fontSize=13.sp,color=Muted)
                        Spacer(Modifier.height(16.dp))
                        if(notes.isEmpty()) EmptyState("Nothing here yet","Connect your backend and sync to load call records.")
                        else LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)){items(notes){NoteRow(it)}}
                    }
                    else -> {
                        Text("Settings",fontSize=24.sp,fontWeight=FontWeight.Bold)
                        Text("Private connection settings",fontSize=13.sp,color=Muted)
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(value=baseUrl,onValueChange={baseUrl=it},label={Text("Backend URL (https://…)")},singleLine=true,modifier=Modifier.fillMaxWidth())
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(value=token,onValueChange={token=it},label={Text("Private app token")},singleLine=true,modifier=Modifier.fillMaxWidth())
                        Spacer(Modifier.height(12.dp))
                        Button(onClick={savePrefs();status="Settings saved"},modifier=Modifier.fillMaxWidth()){Text("SAVE SETTINGS")}
                        Spacer(Modifier.height(20.dp))
                        Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(18.dp)) { Column(Modifier.padding(16.dp)) {
                            Text("Jio forwarding setup",fontWeight=FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Text("Use MyJio → Profile → Mobile Settings → Service Settings → Call Forwarding. Do not enable forwarding until the provider destination number and backend webhook have been tested.",fontSize=13.sp,color=Muted,lineHeight=19.sp)
                        }}
                    }
                }
            }
        }
    }
}

@Composable private fun StatCard(value:String,label:String,modifier:Modifier){ Card(modifier,colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(18.dp)){Column(Modifier.padding(16.dp)){Text(value,fontSize=24.sp,fontWeight=FontWeight.Bold,color=Mint);Text(label,fontSize=10.sp,color=Muted,letterSpacing=1.sp)}} }
@Composable private fun EmptyState(title:String,body:String){ Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(18.dp)){Text(title,fontWeight=FontWeight.SemiBold);Spacer(Modifier.height(4.dp));Text(body,color=Muted,fontSize=13.sp)}} }
@Composable private fun NoteRow(note:Note){ Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp)){Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(note.name,fontWeight=FontWeight.SemiBold);Text(note.number,fontSize=12.sp,color=Muted)};Text(note.time,fontSize=11.sp,color=Muted)};Spacer(Modifier.height(7.dp));Text(note.summary.ifBlank{"No summary available"},fontSize=13.sp);if(note.transcript.isNotBlank()){Spacer(Modifier.height(5.dp));Text(note.transcript,fontSize=11.sp,color=Muted,maxLines=3)}}} }

private suspend fun updateAssistant(base:String,token:String,enabled:Boolean) = withContext(Dispatchers.IO) {
    require(base.startsWith("https://")) { "Backend URL must use HTTPS" }
    val c=(URL(base.trimEnd('/')+"/api/v1/assistant").openConnection() as HttpURLConnection).apply { requestMethod="PUT";doOutput=true;connectTimeout=10000;readTimeout=15000;setRequestProperty("Content-Type","application/json");setRequestProperty("Authorization","Bearer $token") }
    try { c.outputStream.use { it.write(JSONObject().put("enabled",enabled).toString().toByteArray()) }; if(c.responseCode !in 200..299) throw IllegalStateException("HTTP ${c.responseCode}") } finally { c.disconnect() }
}

private suspend fun fetchNotes(base:String,token:String):List<Note> = withContext(Dispatchers.IO) {
    require(base.startsWith("https://")) { "Backend URL must use HTTPS" }
    val c=(URL(base.trimEnd('/')+"/api/v1/calls").openConnection() as HttpURLConnection).apply { requestMethod="GET";connectTimeout=10000;readTimeout=15000;setRequestProperty("Authorization","Bearer $token") }
    try { if(c.responseCode !in 200..299) throw IllegalStateException("HTTP ${c.responseCode}"); val arr=JSONArray(c.inputStream.bufferedReader().use{it.readText()}); (0 until arr.length()).map { i -> val o=arr.getJSONObject(i); Note(o.optString("callId"),o.optString("callerName","Unknown caller"),o.optString("callerNumber"),o.optString("summary"),o.optString("transcript"),o.optString("displayTime")) } } finally { c.disconnect() }
}
