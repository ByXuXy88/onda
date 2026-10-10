package es.jesus.ampodcasts;

import androidx.media3.common.*;
import java.util.*;

/** The session cannot start unanalysed audio; the underlying player is kept paused. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class GeminiPlaybackGate extends ForwardingPlayer {
    interface Owner { boolean automatic(); boolean needsPreparation(); void requestPlay(); void cancelPreparation(); void changingItem(String id); }
    private final Player delegate;private final Owner owner;
    GeminiPlaybackGate(Player player,Owner owner){super(player);delegate=player;this.owner=owner;}
    @Override public void play(){owner.requestPlay();}
    @Override public void setPlayWhenReady(boolean ready){if(ready)owner.requestPlay();else pause();}
    @Override public void pause(){owner.cancelPreparation();delegate.pause();}
    @Override public void stop(){owner.cancelPreparation();delegate.stop();}
    @Override public void prepare(){if(!owner.needsPreparation())delegate.prepare();}
    @Override public void clearMediaItems(){owner.changingItem("");delegate.clearMediaItems();}
    @Override public void setMediaItems(List<MediaItem> items,int index,long position){
        if(items.isEmpty()){clearMediaItems();return;}int selected=index==C.INDEX_UNSET?0:index;
        if(selected<0 || selected>=items.size())throw new IllegalArgumentException("Índice de episodio no válido");
        owner.changingItem(items.get(selected).mediaId);
        if(owner.automatic()){delegate.pause();delegate.setMediaItems(Collections.singletonList(items.get(selected)),0,position);}
        else delegate.setMediaItems(items,index,position);
    }
    @Override public void setMediaItems(List<MediaItem> items){setMediaItems(items,0,0);}
    @Override public void setMediaItems(List<MediaItem> items,boolean reset){int index=reset?0:Math.max(0,delegate.getCurrentMediaItemIndex());setMediaItems(items,items.isEmpty()?0:Math.min(index,items.size()-1),reset?0:delegate.getCurrentPosition());}
    @Override public void setMediaItem(MediaItem item){setMediaItems(Collections.singletonList(item),0,0);}
    @Override public void setMediaItem(MediaItem item,long position){setMediaItems(Collections.singletonList(item),0,position);}
    @Override public void setMediaItem(MediaItem item,boolean reset){setMediaItems(Collections.singletonList(item),0,reset?0:delegate.getCurrentPosition());}
}
