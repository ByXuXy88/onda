package es.jesus.ampodcasts;

import static org.junit.Assert.*;
import android.content.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.io.*;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class GeminiBetaTest {
    private final Context context=RuntimeEnvironment.getApplication();
    @Test public void detectionSortsMergesAndRejectsInvalidOrPartialIntervals() throws Exception {
        List<AdSegments.Segment> segments=AdSegments.parseDetection("{\"segments\":[{\"start_seconds\":20,\"end_seconds\":35},{\"start_seconds\":10,\"end_seconds\":25},{\"start_seconds\":50,\"end_seconds\":60}]}",90000);
        assertEquals(2,segments.size());assertEquals(10000,segments.get(0).start);assertEquals(35000,segments.get(0).end);
        String[] bad={"{\"segments\":[{\"start_seconds\":-1,\"end_seconds\":5}]}","{\"segments\":[{\"start_seconds\":10,\"end_seconds\":5}]}","{\"segments\":[{\"start_seconds\":10,\"end_seconds\":100}]}","{\"segments\":[{\"start_seconds\":\"10\",\"end_seconds\":20}]}","{\"segments\":[{\"start_seconds\":0,\"end_seconds\":0.0001}]}","not json"};
        for(String text:bad)try{AdSegments.parseDetection(text,90000);fail(text);}catch(Exception expected){}
        assertTrue(AdSegments.parseDetection("{\"segments\":[]}",90000).isEmpty());
    }
    @Test public void automaticIntervalsAreEndExclusiveAndUndoExcludesOneSegmentPersistently() throws Exception {
        AdSegments.Record record=new AdSegments.Record(7,90000,"https://example.com/a.mp3","gemini-test",Arrays.asList(new AdSegments.Segment(10000,20000,"Anuncio"),new AdSegments.Segment(30000,40000,"Otro")),new HashSet<>(),true);
        AdSegments.save(context,"a",record);assertNull(AdSegments.load(context,"b"));
        assertNull(record.at(9999,90000));assertNotNull(record.at(10000,90000));assertNotNull(record.at(19999,90000));assertNull(record.at(20000,90000));assertNull(record.at(15000,120000));
        AdSegments.ignore(context,"a",10000);assertNull(AdSegments.load(context,"a").at(15000,90000));assertNotNull(AdSegments.load(context,"a").at(35000,90000));
        AdSegments.enabled(context,"a",false);assertNull(AdSegments.load(context,"a").at(35000,90000));AdSegments.enabled(context,"a",true);assertNull(AdSegments.load(context,"a").at(15000,90000));
        AdSegments.remove(context,"a");assertNull(AdSegments.load(context,"a"));
    }
    @Test public void keyEncryptionRejectsTamperingAndBackupsExcludeCredentialsAndAdResults() throws Exception {
        SecretKeySpec key=new SecretKeySpec(new byte[32],"AES");String secret="test-only-secret-never-a-real-api-key";
        String encrypted=GeminiKeyStore.seal(secret,key);assertFalse(encrypted.contains(secret));assertEquals(secret,GeminiKeyStore.open(encrypted,key));assertNotEquals(encrypted,GeminiKeyStore.seal(secret,key));
        String changed=encrypted.substring(0,encrypted.indexOf(':')+1)+"AAAA";try{GeminiKeyStore.open(changed,key);fail();}catch(Exception expected){}
        GeminiKeyStore.prefs(context).edit().putString("encrypted_key",encrypted).putString("model","gemini-test").commit();
        Repository r=new Repository(context);r.prefs.edit().putString("encrypted_key",secret).putString(AdSegments.key("a"),"test").commit();
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();BackupStore.export(context,bytes);String backup=bytes.toString("UTF-8");assertFalse(backup.contains(secret));assertFalse(backup.contains("encrypted_key"));assertFalse(backup.contains("ads:"));
    }
    @Test public void settingsAndEpisodeScreenExposeBetaWithoutSendingAudioOnOpen() {
        try(org.robolectric.android.controller.ActivityController<GeminiAdsActivity> activity=Robolectric.buildActivity(GeminiAdsActivity.class).setup()){
            View root=activity.get().getWindow().getDecorView();assertNotNull(find(root,"Anuncios · Gemini Beta"));assertNotNull(find(root,GeminiAdsActivity.WARNING));assertNotNull(find(root,"Añadir clave API"));assertNull(find(root,"Cancelar"));
            assertTrue((activity.get().getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE)!=0);
        }
        try(org.robolectric.android.controller.ActivityController<SettingsActivity> activity=Robolectric.buildActivity(SettingsActivity.class).setup()){
            View button=find(activity.get().getWindow().getDecorView(),"Saltar anuncios · Gemini Beta");assertNotNull(button);button.performClick();Intent intent=Shadows.shadowOf(activity.get()).getNextStartedActivity();assertEquals(GeminiAdsActivity.class.getName(),intent.getComponent().getClassName());
        }
    }
    private View find(View v,String text){if(v instanceof TextView && text.contentEquals(((TextView)v).getText()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=find(((ViewGroup)v).getChildAt(i),text);if(found!=null)return found;}return null;}
}
