package be.humanoids.webthingify;

import android.os.AsyncTask;
import android.util.Log;

import org.mozilla.iot.webthing.Thing;
import org.mozilla.iot.webthing.WebThingServer;

import java.io.IOException;

class ServerTask extends AsyncTask<Thing, Void, WebThingServer> {
    private WebThingServer server;

    @Override
    protected WebThingServer doInBackground(Thing... things) {
        try {
            WebThingServer server = new WebThingServer(new WebThingServer.SingleThing(things[0]), 88880);
            server.start(true);
            return server;
        } catch (IOException e) {
            Log.e("wt:server", "Error starting server", e);
        }
        return null;
    }

    protected void onPostExecute(WebThingServer server) {
        this.server = server;
    }

    public void onDestroy() {
        if(server != null) {
            server.stop();
            server = null;
        }
    }
}
