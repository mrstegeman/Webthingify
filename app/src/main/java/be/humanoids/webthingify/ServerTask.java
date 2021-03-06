package be.humanoids.webthingify;

import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import org.mozilla.iot.webthing.Thing;
import org.mozilla.iot.webthing.Utils;
import org.mozilla.iot.webthing.WebThingServer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;

import fi.iki.elonen.router.RouterNanoHTTPD;

class ServerTask extends AsyncTask<Thing, Void, WebThingServer> {
    final public static int DEFAULT_PORT = 8088;

    private final ResultHandler delegate;
    private @Nullable
    WebThingServer server;
    private boolean shouldBeRunning = true;
    final private File tempDir;
    final private int port;

    ServerTask(@NonNull ResultHandler handler, File tempDir, int port) {
        super();
        this.delegate = handler;
        this.tempDir = tempDir;
        this.port = port;
    }

    @Override
    protected WebThingServer doInBackground(Thing... things) {
        try {
            WebThingServer.Route camRoute = new WebThingServer.Route("/static/(.+)",
                    RouterNanoHTTPD.StaticPageHandler.class,
                    new Object[]{
                            this.tempDir
                    });
            WebThingServer server = new WebThingServer(new WebThingServer.SingleThing(things[0]),
                    port,
                    null,
                    null,
                    Collections.singletonList(camRoute));
            server.start(false);
            for (String address : Utils.getAddresses()) {
                Log.i("wt:server", address + ":" + Integer.toString(server.getListeningPort()));
            }
            return server;
        } catch (IOException e) {
            Log.e("wt:server", "Error starting server", e);
        }
        return null;
    }

    protected void onPostExecute(@Nullable WebThingServer server) {
        this.server = server;
        if (!shouldBeRunning) {
            onDestroy();
        } else {
            this.delegate.handleResult(server != null);
        }
    }

    void onDestroy() {
        shouldBeRunning = false;
        if (server != null) {
            server.stop();
            server = null;
            Log.i("wt:server", "stopped");
        }
    }

    public interface ResultHandler {
        void handleResult(boolean isRunning);
    }
}
