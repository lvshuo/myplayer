package com.example.andylv.myplayer;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.source.BehindLiveWindowException;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.DefaultSsChunkSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveVideoTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.ui.DebugTextViewHelper;
import com.google.android.exoplayer2.ui.PlaybackControlView;
import com.google.android.exoplayer2.ui.SimpleExoPlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.Util;
//import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo;

import java.io.IOException;
import java.net.CookieHandler;
import java.util.ArrayList;
import java.util.Arrays;

public class MainActivity extends Activity  implements View.OnClickListener, ExoPlayer.EventListener,
        PlaybackControlView.VisibilityListener {


    private static final DefaultBandwidthMeter BANDWIDTH_METER = new DefaultBandwidthMeter();


    private Handler mainHandler;
   // private EventLogger eventLogger;
    private SimpleExoPlayerView simpleExoPlayerView;
    private LinearLayout debugRootView;
    private TextView debugTextView;
    private Button retryButton;

    private DataSource.Factory mediaDataSourceFactory;
    private  ExtractorMediaSource extractorsFactory;
    private SimpleExoPlayer player;
    private DefaultTrackSelector trackSelector;
    private DefaultLoadControl loadControl;
   // private TrackSelectionHelper trackSelectionHelper;
    private DebugTextViewHelper debugViewHelper;
    private boolean playerNeedsSource;

    private boolean shouldAutoPlay;
    private int resumeWindow;
    private long resumePosition;
    private Uri contentUri;
    private String[] uris;
    private ArrayList<String> uriList;

    private LogUtil logUtil;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        shouldAutoPlay = true;
        clearResumePosition();
        mediaDataSourceFactory = buildDataSourceFactory(true);
        mainHandler = new Handler();

//        if (CookieHandler.getDefault() != DEFAULT_COOKIE_MANAGER) {
//            CookieHandler.setDefault(DEFAULT_COOKIE_MANAGER);
//        }
       ///////////////////////////////////////
        Intent intent = getIntent();
        String dataUri = intent.getDataString();

        if (dataUri != null) {
            uris = new String[] {dataUri};
        } else {
            uriList = new ArrayList<>();
            AssetManager assetManager = getAssets();
            try {
                for (String asset : assetManager.list("")) {
                    if (asset.endsWith(".exolist.json")) {
                        uriList.add("asset:///" + asset);
                    }
                }
            } catch (IOException e) {
                Toast.makeText(getApplicationContext(),  "One or more lists failed to load", Toast.LENGTH_LONG)
                        .show();
            }
            uris = new String[uriList.size()];
            uriList.toArray(uris);
            Arrays.sort(uris);
        }
        ////////////////////////////////////////////////////////////////////////////////

        setContentView(R.layout.activity_main);
        View rootView = findViewById(R.id.root);
        rootView.setOnClickListener(this);
        debugRootView = (LinearLayout) findViewById(R.id.controls_root);
        debugTextView = (TextView) findViewById(R.id.debug_text_view);
        retryButton = (Button) findViewById(R.id.retry_button);
        retryButton.setOnClickListener(this);



        simpleExoPlayerView = (SimpleExoPlayerView) findViewById(R.id.player_view);
        simpleExoPlayerView.setControllerVisibilityListener(this);
        simpleExoPlayerView.requestFocus();


    }
    @Override
    public void onNewIntent(Intent intent) {
        releasePlayer();
        shouldAutoPlay = true;
        clearResumePosition();
        setIntent(intent);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (Util.SDK_INT > 23) {
            initializePlayer();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if ((Util.SDK_INT <= 23 || player == null)) {
            initializePlayer();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (Util.SDK_INT <= 23) {
            releasePlayer();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (Util.SDK_INT > 23) {
            releasePlayer();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initializePlayer();
        } else {
            showToast("Permission to access storage was denied");
            finish();
        }
    }

    // Activity input

//    @Override
//    public boolean dispatchKeyEvent(KeyEvent event) {
//        // Show the controls on any key event.
//        //simpleExoPlayerView.
//        // If the event was not handled then see if the player view can handle it as a media key event.
//       // return super.dispatchKeyEvent(event) || simpleExoPlayerView.dispatchMediaKeyEvent(event);
//    }

    // OnClickListener methods

    @Override
    public void onClick(View view) {
        if (view == retryButton) {
            initializePlayer();
        } else if (view.getParent() == debugRootView) {

//            MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
//            if (mappedTrackInfo != null) {
//                trackSelectionHelper.showSelectionDialog(this, ((Button) view).getText(),
//                        trackSelector.getCurrentMappedTrackInfo(), (int) view.getTag());
        }
    }

    // PlaybackControlView.VisibilityListener implementation

    @Override
    public void onVisibilityChange(int visibility) {
        debugRootView.setVisibility(visibility);
    }

    // Internal methods
    private void initializePlayer() {
        Intent intent = getIntent();
        if(player==null){
            Handler mainHandler = new Handler();
            TrackSelection.Factory videoTrackSelectionFactory =
                    new AdaptiveVideoTrackSelection.Factory(BANDWIDTH_METER);

            trackSelector = new DefaultTrackSelector(mainHandler,videoTrackSelectionFactory);

//            trackSelectionHelper = new TrackSelectionHelper(trackSelector, videoTrackSelectionFactory);
           // player = ExoPlayerFactory.newSimpleInstance(this, trackSelector, new DefaultLoadControl(),
           //        drmSessionManager, extensionRendererMode);
            loadControl= new DefaultLoadControl();
            player=ExoPlayerFactory.newSimpleInstance(this, trackSelector, loadControl);
            //player.addListener(this);
//
//            eventLogger = new EventLogger(trackSelector);
//            player.addListener(eventLogger);
//            player.setAudioDebugListener(eventLogger);
//            player.setVideoDebugListener(eventLogger);
//            player.setMetadataOutput(eventLogger);

            simpleExoPlayerView.setPlayer(player);
          //  player.setPlayWhenReady(shouldAutoPlay);
          //  debugViewHelper = new DebugTextViewHelper(player, debugTextView);
          //  debugViewHelper.start();
            playerNeedsSource = true;


        }


        if(playerNeedsSource){

            logUtil.d("TAG","my debug log");

            String action = intent.getAction();

            Uri[] uris_1;
            uris_1=new Uri[uris.length];

            for (int i = 0; i < uris_1.length; i++) {
                uris_1[i] = Uri.parse(uris[i]);
            }
            if (Util.maybeRequestReadExternalStoragePermission(this, uris_1)) {
                // The player will be reinitialized if the permission is granted.
                return;
            }



//            MediaSource[] mediaSources = new MediaSource[uris_1.length];
//            for (int i = 0; i < uris_1.length; i++) {
//                mediaSources[i] = buildMediaSource(uris_1[i]);   //extensions[i]);
//            }
//            MediaSource mediaSource = mediaSources.length == 1 ? mediaSources[0]
//                    : new ConcatenatingMediaSource(mediaSources);
//            boolean haveResumePosition = resumeWindow != C.INDEX_UNSET;
//            if (haveResumePosition) {
//                player.seekTo(resumeWindow, resumePosition);
//            }
             // Measures bandwidth during playback. Can be null if not required.
            DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
              // Produces DataSource instances through which media data is loaded.
            DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(this, Util.getUserAgent(this, "ExoPlayerDemo"), bandwidthMeter);
            // Produces Extractor instances for parsing the media data.


            Uri uri_test;
           // uri_test=Uri.parse("http://www.youtube.com/api/manifest/dash/id/bf5bb2419360daf1/source/youtube?as=fmp4_audio_clear,fmp4_sd_hd_clear&sparams=ip,ipbits,expire,source,id,as&ip=0.0.0.0&ipbits=0&expire=19000000000&signature=51AF5F39AB0CEC3E5497CD9C900EBFEAECCCB5C7.8506521BFC350652163895D4C26DEE124209AA9E&key=ik0");
           //uri_test=Uri.parse(" http://www.youtube.com/api/manifest/dash/id/3aa39fa2cc27967f/source/youtube?as=fmp4_audio_clear,fmp4_sd_hd_clear&sparams=ip,ipbits,expire,source,id,as&ip=0.0.0.0&ipbits=0&expire=19000000000&signature=A2716F75795F5D2AF0E88962FFCD10DB79384F29.84308FF04844498CE6FBCE4731507882B8307798&key=ik0");
           //  uri_test = Uri.parse("file://"+Environment.getExternalStorageDirectory().getPath()+"/VID_20170215_173705.mp4");
            uri_test = Uri.parse("file://sdcard/DCIM/Camera/VID_20170215_173705.mp4");
           MediaSource mediaSource_1 =new ExtractorMediaSource(uri_test, mediaDataSourceFactory, new DefaultExtractorsFactory(),null, null);
            //MediaSource mediaSource_1 =new DashMediaSource(uri_test, buildDataSourceFactory(false),
              //          new DefaultDashChunkSource.Factory(mediaDataSourceFactory), mainHandler, null);

            //player.prepare(mediaSource_1, !haveResumePosition, false);

           player.prepare(mediaSource_1);
            playerNeedsSource = false;
            updateButtonVisibilities();
        }
    }
    private MediaSource buildMediaSource(Uri uri){
        int type = Util.inferContentType(uri.getLastPathSegment());

//        switch (type) {
//            case C.TYPE_SS:
//                return new SsMediaSource(uri, buildDataSourceFactory(false),
//                        new DefaultSsChunkSource.Factory(mediaDataSourceFactory), mainHandler, null);
//            case C.TYPE_DASH:
//                return new DashMediaSource(uri, buildDataSourceFactory(false),
//                        new DefaultDashChunkSource.Factory(mediaDataSourceFactory), mainHandler, null);
//            case C.TYPE_HLS:
//                return new HlsMediaSource(uri, mediaDataSourceFactory, mainHandler, null);
//            case C.TYPE_OTHER:
//                return new ExtractorMediaSource(uri, mediaDataSourceFactory, new DefaultExtractorsFactory(),
//                        mainHandler, null);
//            default: {
//                throw new IllegalStateException("Unsupported type: " + type);
//            }
//        }

        return new ExtractorMediaSource(uri, mediaDataSourceFactory, new DefaultExtractorsFactory(),
                        mainHandler, null);
    }

//    private MediaSource buildMediaSource(Uri uri, String overrideExtension) {
//        int type = Util.inferContentType(!TextUtils.isEmpty(overrideExtension) ? "." + overrideExtension
//                : uri.getLastPathSegment());
//        switch (type) {
//            case C.TYPE_SS:
//                return new SsMediaSource(uri, buildDataSourceFactory(false),
//                        new DefaultSsChunkSource.Factory(mediaDataSourceFactory), mainHandler, null);
//            case C.TYPE_DASH:
//                return new DashMediaSource(uri, buildDataSourceFactory(false),
//                        new DefaultDashChunkSource.Factory(mediaDataSourceFactory), mainHandler, null);
//            case C.TYPE_HLS:
//                return new HlsMediaSource(uri, mediaDataSourceFactory, mainHandler, null);
//            case C.TYPE_OTHER:
//                return new ExtractorMediaSource(uri, mediaDataSourceFactory, new DefaultExtractorsFactory(),
//                        mainHandler, null);
//            default: {
//                throw new IllegalStateException("Unsupported type: " + type);
//            }
//        }
//    }

    private void releasePlayer() {
        if (player != null) {
            debugViewHelper.stop();
            debugViewHelper = null;
            shouldAutoPlay = player.getPlayWhenReady();
            updateResumePosition();
            player.release();
            player = null;
            trackSelector = null;
           // trackSelectionHelper = null;
           // eventLogger = null;
        }
    }

    private void updateResumePosition() {
        resumeWindow = player.getCurrentWindowIndex();
//        resumePosition = player.isCurrentWindowSeekable() ? Math.max(0, player.getCurrentPosition())
//                : C.TIME_UNSET;
    }

    private void clearResumePosition() {
        resumeWindow = C.INDEX_UNSET;
        resumePosition = C.TIME_UNSET;
    }

    /**
     * Returns a new DataSource factory.
     *
     * @param useBandwidthMeter Whether to set {@link #BANDWIDTH_METER} as a listener to the new
     *     DataSource factory.
     * @return A new DataSource factory.
     */
    private DataSource.Factory buildDataSourceFactory(boolean useBandwidthMeter) {
        return ((DemoApplication) getApplication())
                .buildDataSourceFactory(useBandwidthMeter ? BANDWIDTH_METER : null);
    }

    /**
     * Returns a new HttpDataSource factory.
     *
     * @param useBandwidthMeter Whether to set {@link #BANDWIDTH_METER} as a listener to the new
     *     DataSource factory.
     * @return A new HttpDataSource factory.
     */
    private HttpDataSource.Factory buildHttpDataSourceFactory(boolean useBandwidthMeter) {
        return ((DemoApplication) getApplication())
                .buildHttpDataSourceFactory(useBandwidthMeter ? BANDWIDTH_METER : null);
    }


    // ExoPlayer.EventListener implementation

    @Override
    public void onLoadingChanged(boolean isLoading) {
        // Do nothing.
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        if (playbackState == ExoPlayer.STATE_ENDED) {
            showControls();
        }
        updateButtonVisibilities();
    }

    @Override
    public void onPositionDiscontinuity() {
        if (playerNeedsSource) {
            // This will only occur if the user has performed a seek whilst in the error state. Update the
            // resume position so that if the user then retries, playback will resume from the position to
            // which they seeked.
            updateResumePosition();
        }
    }

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest) {
        // Do nothing.
    }

    @Override
    public void onPlayerError(ExoPlaybackException e) {
        String errorString = null;
        if (e.type == ExoPlaybackException.TYPE_RENDERER) {
            Exception cause = e.getRendererException();
            if (cause instanceof MediaCodecRenderer.DecoderInitializationException) {
                // Special case for decoder initialization failures.
                MediaCodecRenderer.DecoderInitializationException decoderInitializationException =
                        (MediaCodecRenderer.DecoderInitializationException) cause;
                if (decoderInitializationException.decoderName == null) {
                    if (decoderInitializationException.getCause() instanceof MediaCodecUtil.DecoderQueryException) {
                        errorString = "unable to qurery device decoders";
                    } else if (decoderInitializationException.secureDecoderRequired) {
                        errorString = "This device dose not provide a secure decoder for";
                    } else {
                        errorString = "this device dose not provide a decoder for ";
                    }
                } else {
                    errorString = "Unable to instantiate decoder";
                }
            }
        }
        if (errorString != null) {
            showToast(errorString);
        }
        playerNeedsSource = true;
        if (isBehindLiveWindow(e)) {
            clearResumePosition();
            initializePlayer();
        } else {
            updateResumePosition();
            updateButtonVisibilities();
            showControls();
        }
    }



    // User controls

    private void updateButtonVisibilities() {
        debugRootView.removeAllViews();

        retryButton.setVisibility(playerNeedsSource ? View.VISIBLE : View.GONE);
        debugRootView.addView(retryButton);

        if (player == null) {
            return;
        }

//        MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
//        if (mappedTrackInfo == null) {
//            return;
//        }
//
//        for (int i = 0; i < mappedTrackInfo.length; i++) {
//            TrackGroupArray trackGroups = mappedTrackInfo.getTrackGroups(i);
//            if (trackGroups.length != 0) {
//                Button button = new Button(this);
//                int label;
//                switch (player.getRendererType(i)) {
//                    case C.TRACK_TYPE_AUDIO:
//                        label = R.string.audio;
//                        break;
//                    case C.TRACK_TYPE_VIDEO:
//                        label = R.string.video;
//                        break;
//                    case C.TRACK_TYPE_TEXT:
//                        label = R.string.text;
//                        break;
//                    default:
//                        continue;
//                }
//                button.setText(label);
//                button.setTag(i);
//                button.setOnClickListener(this);
//                debugRootView.addView(button, debugRootView.getChildCount() - 1);
//            }
//       }
    }

    private void showControls() {
        debugRootView.setVisibility(View.VISIBLE);
    }

    private void showToast(int messageId) {
        showToast(getString(messageId));
    }

    private void showToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
    }

    private static boolean isBehindLiveWindow(ExoPlaybackException e) {
        if (e.type != ExoPlaybackException.TYPE_SOURCE) {
            return false;
        }
        Throwable cause = e.getSourceException();
        while (cause != null) {
            if (cause instanceof BehindLiveWindowException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
