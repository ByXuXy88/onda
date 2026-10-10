package es.jesus.ampodcasts;

import static org.junit.Assert.*;
import android.content.Context;
import android.net.Uri;
import androidx.media3.common.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.util.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class GeminiTopicsTest {
    static JSONObject data() throws Exception {return new JSONObject("{\"chapters\":[{\"start_seconds\":10,\"end_seconds\":100,\"title\":\"Ciencia y tecnología\",\"summary\":\"Conversan sobre ciencia y tecnología.\",\"subtopics\":[{\"start_seconds\":10,\"end_seconds\":50,\"title\":\"Astronomía\"},{\"start_seconds\":50,\"end_seconds\":100,\"title\":\"Robótica\"}]}],\"topics\":[\"Ciencia\",\"Tecnología\"]}");}
    @Test public void chaptersKeepGroupedSubtopicsAndBoundedTimes() throws Exception {GeminiTopics.Content c=GeminiTopics.parse(data(),120000,new ArrayList<>());assertEquals(1,c.chapters.size());assertEquals(10000,c.chapters.get(0).start);assertEquals("Robótica",c.chapters.get(0).children.get(1).title);assertEquals(50000,c.chapters.get(0).children.get(1).start);assertEquals(c.topics,GeminiTopics.parse(GeminiTopics.encode(c),120000,new ArrayList<>()).topics);}
    @Test public void malformedOrOverlappingChildTimesCannotBecomeNavigation() throws Exception {for(String key:new String[]{"start_seconds","end_seconds"}){JSONObject d=data();JSONObject child=d.getJSONArray("chapters").getJSONObject(0).getJSONArray("subtopics").getJSONObject(0);child.put(key,key.equals("start_seconds")?0:110);try{GeminiTopics.parse(d,120000,new ArrayList<>());fail();}catch(java.io.IOException expected){}}JSONObject overlap=data();overlap.getJSONArray("chapters").getJSONObject(0).getJSONArray("subtopics").getJSONObject(1).put("start_seconds",49);try{GeminiTopics.parse(overlap,120000,new ArrayList<>());fail();}catch(java.io.IOException expected){} }
    @Test public void adOnlyChapterIsExcludedAndSchemaRequiresBothOutputs() throws Exception {assertTrue(GeminiTopics.parse(data(),120000,Arrays.asList(new AdSegments.Segment(0,110000,"Ad"))).chapters.isEmpty());JSONObject payload=GeminiClient.payload(GeminiClient.BASE+"/v1beta/files/test","audio/mpeg",120000);JSONObject schema=payload.getJSONObject("generationConfig").getJSONObject("responseSchema");assertTrue(schema.getJSONObject("properties").has("chapters"));assertTrue(schema.getJSONObject("properties").has("topics"));assertEquals(3,schema.getJSONArray("required").length());}
    @Test public void adTogglesAndUndoPreserveThemesAndOldRecordsRemainReadable() throws Exception {Context c=RuntimeEnvironment.getApplication();GeminiTopics.Content themes=GeminiTopics.parse(data(),120000,new ArrayList<>());AdSegments.Record record=new AdSegments.Record(1,120000,"https://example.com/audio.mp3","gemini-test",Arrays.asList(new AdSegments.Segment(0,9000,"Ad")),new HashSet<>(),true,"",0,themes);AdSegments.save(c,"test",record);AdSegments.ignore(c,"test",0);AdSegments.enabled(c,"test",false);assertEquals("Robótica",AdSegments.load(c,"test").themes.chapters.get(0).children.get(1).title);assertTrue(AdSegments.load(c,"test").ignored.contains(0L));JSONObject old=new JSONObject(AdSegments.encode(record));old.remove("themes");assertNull(AdSegments.decode(old.toString()).themes);}
    @Test public void playerMetadataBindsNavigationEvenWhenControllerOmitsLocalConfiguration() throws Exception {Uri uri=Uri.parse("file:///private/audio");android.os.Bundle extras=new android.os.Bundle();extras.putString("audioBinding",Repository.key(uri.toString()));MediaItem transported=new MediaItem.Builder().setMediaId("test").setMediaMetadata(new MediaMetadata.Builder().setExtras(extras).build()).build();assertNull(transported.localConfiguration);assertTrue(GeminiTopics.bound(transported,uri));assertFalse(GeminiTopics.bound(transported,Uri.parse("https://example.com/stream")));}
}
