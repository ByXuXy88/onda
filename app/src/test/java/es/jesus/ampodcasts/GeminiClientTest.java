package es.jesus.ampodcasts;

import static org.junit.Assert.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.io.*;
import java.util.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class GeminiClientTest {
    private static GeminiClient.Reply reply(int code,String text){return new GeminiClient.Reply(code,text,new HashMap<>());}
    @Test public void uploadAnalyzeAndDeleteFollowOfficialProtocolWithoutEmbeddingKeyInUrls() throws Exception {
        List<String> calls=new ArrayList<>();byte[] audio={1,2,3,4};
        GeminiClient client=new GeminiClient("test-key",(method,url,headers,size,body)->{
            calls.add(method+" "+url);assertFalse(url.contains("test-key"));assertEquals("test-key",headers.get("x-goog-api-key"));
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();if(body!=null)body.write(bytes);assertEquals(size,bytes.size());
            if(url.contains("/upload/v1beta/files")){assertEquals("resumable",headers.get("X-Goog-Upload-Protocol"));assertEquals("4",headers.get("X-Goog-Upload-Header-Content-Length"));return new GeminiClient.Reply(200,"{}",Collections.singletonMap("x-goog-upload-url",GeminiClient.BASE+"/upload/session"));}
            if(url.endsWith("/upload/session")){assertArrayEquals(audio,bytes.toByteArray());assertEquals("upload, finalize",headers.get("X-Goog-Upload-Command"));return reply(200,"{\"file\":{\"name\":\"files/test\",\"uri\":\"https://generativelanguage.googleapis.com/v1beta/files/test\",\"state\":\"ACTIVE\"}}");}
            if(method.equals("DELETE"))return reply(200,"{}");
            JSONObject payload=new JSONObject(bytes.toString("UTF-8"));assertEquals("application/json",payload.getJSONObject("generationConfig").getString("responseMimeType"));assertEquals("audio/mpeg",payload.getJSONArray("contents").getJSONObject(0).getJSONArray("parts").getJSONObject(0).getJSONObject("fileData").getString("mimeType"));
            return reply(200,new JSONObject().put("candidates",new JSONArray().put(new JSONObject().put("finishReason","STOP").put("content",new JSONObject().put("parts",new JSONArray().put(new JSONObject().put("thought",true).put("text","ignore thought")).put(new JSONObject().put("text","{\"chapters\":[],\"topics\":[],\"segments\":[{\"start_seconds\":1,\"end_seconds\":2,\"label\":\"Publicidad\"}]}")))))).toString());
        });
        List<AdSegments.Segment> segments=client.analyze(()->new ByteArrayInputStream(audio),4,"audio/mpeg",10000,"gemini-test",value->{});
        assertEquals(1000,segments.get(0).start);assertEquals(4,calls.size());assertTrue(calls.get(3).startsWith("DELETE "));
    }
    @Test public void quotaOrTruncatedResultsAreNotRetriedAndUploadedFileIsDeleted() throws Exception {
        for(boolean truncated:new boolean[]{false,true}){
            List<String> calls=new ArrayList<>();GeminiClient client=new GeminiClient("secret",(method,url,h,size,body)->{calls.add(method+url);if(url.contains("/upload/v1beta/files"))return new GeminiClient.Reply(200,"{}",Collections.singletonMap("X-Goog-Upload-URL",GeminiClient.BASE+"/upload/session"));if(url.endsWith("/upload/session"))return reply(200,"{\"file\":{\"name\":\"files/test\",\"uri\":\"https://generativelanguage.googleapis.com/v1beta/files/test\",\"state\":\"ACTIVE\"}}");if(method.equals("DELETE"))return reply(200,"{}");return truncated?reply(200,"{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\"}]}"):reply(429,"server may echo secret");});
            try{client.analyze(()->new ByteArrayInputStream(new byte[]{0}),1,"audio/mpeg",10000,"gemini-test",s->{});fail();}catch(IOException e){assertFalse(e.getMessage().contains("secret"));}
            assertEquals(4,calls.size());assertTrue(calls.get(3).startsWith("DELETE"));
        }
    }
    @Test public void untrustedUploadDestinationCannotReceiveAudioOrCredential() throws Exception {
        int[] requests={0};GeminiClient client=new GeminiClient("secret",(m,u,h,n,b)->{requests[0]++;return new GeminiClient.Reply(200,"{}",Collections.singletonMap("X-Goog-Upload-URL","https://other.example/upload"));});
        try{client.analyze(()->{fail("Audio must not be opened");return null;},1,"audio/mpeg",10000,"gemini-test",s->{});fail();}catch(IOException expected){}assertEquals(1,requests[0]);
        for(String bad:new String[]{"http://generativelanguage.googleapis.com/upload","https://generativelanguage.googleapis.com.evil.example/upload","https://user:password@generativelanguage.googleapis.com/upload"})try{GeminiClient.trusted(bad);fail();}catch(IOException expected){}
    }
    @Test public void cancellationBeforeStartMakesNoNetworkRequests() throws Exception {
        GeminiClient client=new GeminiClient("secret",(m,u,h,n,b)->{fail();return null;});client.cancel();try{client.analyze(()->null,1,"audio/mpeg",10000,"gemini-test",s->{});fail();}catch(InterruptedIOException expected){}
    }
    @Test public void modelDiscoveryFiltersGenerationAndSupportsPagination() throws Exception {
        int[] requests={0};GeminiClient client=new GeminiClient("secret",(m,u,h,n,b)->{requests[0]++;if(requests[0]==1)return reply(200,"{\"models\":[{\"name\":\"models/gemini-test-flash\",\"supportedGenerationMethods\":[\"generateContent\"]},{\"name\":\"models/gemini-test-tts\",\"supportedGenerationMethods\":[\"generateContent\"]}],\"nextPageToken\":\"next\"}");assertTrue(u.contains("pageToken=next"));return reply(200,"{\"models\":[{\"name\":\"models/gemini-test-pro\",\"supportedGenerationMethods\":[\"generateContent\"]}]}");});
        assertEquals(Arrays.asList("gemini-test-flash","gemini-test-pro"),client.models());assertEquals(2,requests[0]);
    }
}
