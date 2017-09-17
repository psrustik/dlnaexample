package com.intersvyaz.dlnaexample;

import org.fourthline.cling.support.contentdirectory.DIDLParser;
import org.fourthline.cling.support.model.DIDLContent;
import org.fourthline.cling.support.model.ProtocolInfo;
import org.fourthline.cling.support.model.Res;
import org.fourthline.cling.support.model.item.Item;
import org.fourthline.cling.support.model.item.Movie;
import org.fourthline.cling.support.model.item.MusicTrack;
import org.fourthline.cling.support.model.item.Photo;
import org.fourthline.cling.support.model.item.PlaylistItem;
import org.seamless.util.MimeType;

import java.io.File;

/**
 * Created by rustik on 17.09.2017.
 */

public class MediaInfo {

    public static enum MediaMIMEType {
        VIDEO, AUDIO, IMAGE
    }

    private String url;

    private String title;

    private long size;

    private MimeType mimeType;

    private boolean localMedia;

    private MediaInfo() {
    }

    public static MediaInfo createFromUrl(String url, String mimeType, String title) {
        MediaInfo mediaInfo = new MediaInfo();
        mediaInfo.setUrl(url);
        mediaInfo.setLocalMedia(false);
        // TODO set mimetype

        //rst
        mediaInfo.setMimeType(mimeType);
        mediaInfo.setTitle(title);
        return mediaInfo;
    }

    public static MediaInfo createFromLocal(String url, String title,
                                            long size, String mimeType) {
        MediaInfo mediaInfo = new MediaInfo();
        mediaInfo.setSize(size);
        mediaInfo.setTitle(title);
        mediaInfo.setUrl(url);
        mediaInfo.setLocalMedia(true);
        mediaInfo.setMimeType(mimeType);
        return mediaInfo;
    }

    public String getPushUrl() {
        // TODO
        if (localMedia) {
            //rst fixed
            return null;
//            return HttpServerManager.getInstance().getVirtualUrl()
//                    + System.currentTimeMillis();
        } else {
            return url;
        }
    }

    public String getMetaData() {
        Item media = null;
        if (mimeType != null) {
            if (mimeType.getType().equals("video")) {
                media = new Movie();
            } else if (mimeType.getType().equals("audio")) {
                media = new MusicTrack();
            } else if (mimeType.getType().equals("image")) {
                media = new Photo();
            } else if (mimeType.getType().equals("application")) {
                media = new PlaylistItem();
            } else {
                media = new Movie();
            }
        } else {
            media = new Movie();
        }
        if (title != null) {
            media.setTitle(title);
        }
        Res resource = new Res() {
            @Override
            public String getValue() {
                return getPushUrl();
            }
        };

        resource.setProtocolInfo(new ProtocolInfo(
                mimeType == null ? new MimeType() : mimeType));
        if (size != 0) {
            resource.setSize(size);
        } else {
            if (!url.startsWith("http")) {
                File file = new File(url);
                if (file != null && file.isFile()) {
                    size = file.length();
                    resource.setSize(size);
                }
            }
        }
        media.setId("0");
        media.setParentID("0");
        media.setCreator("System");
        media.addResource(resource);
        DIDLContent didl = new DIDLContent();
        didl.addItem(media);
        String metadata = null;
        try {
            metadata = new DIDLParser().generate(didl);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return metadata;
    }

    public MimeType getMimeType() {
        return mimeType;
    }

    public void setMimeType(MimeType mimeType) {
        this.mimeType = mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = MimeType.valueOf(mimeType);
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public boolean isLocalMedia() {
        return localMedia;
    }

    public void setLocalMedia(boolean localMedia) {
        this.localMedia = localMedia;
    }

    @Override
    public String toString() {
        return "MediaInfo [url=" + url + ", title=" + title + ", size=" + size
                + ", mimeType=" + mimeType + "]";
    }
}