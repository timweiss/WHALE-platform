package de.mimuc.senseeverything.network;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.toolbox.HttpHeaderParser;
import com.google.gson.Gson;

import java.nio.charset.StandardCharsets;

import de.mimuc.senseeverything.service.SEApplicationController;

/**
 * Syncs activities with backend.
 */
public class UploadRequest extends Request<String>  {

    public static final String TAG = "SyncRequest";

    protected Response.Listener<String> responseListener;
    private final SenseEverythingDataBackendWrapper dataWrapper;

    public UploadRequest(Response.Listener<String> responseListener, Response.ErrorListener errorListener, SenseEverythingDataBackendWrapper dataWrapper) {
        super(
                Request.Method.POST,
                "https://analysis.phonestudy.medien.ifi.lmu.de/apache/senseeverything/index.php",
                errorListener
        );
        this.responseListener = responseListener;
        this.dataWrapper = dataWrapper;
    }

    @Override
    protected Response<String> parseNetworkResponse(NetworkResponse response) {
        return Response.success(new String(response.data), HttpHeaderParser.parseCacheHeaders(response));
    }

    @Override
    protected void deliverResponse(String response) {
        responseListener.onResponse(response);
    }

    @Override
    public String getBodyContentType() {
        return "application/json; charset=utf-8";
    }

    public void addToRequestQueue() {
        SEApplicationController.getInstance().addToRequestQueue(this);
    }

    @Override
    public byte[] getBody() throws AuthFailureError {
        Gson gson = new Gson();
        String dataString = gson.toJson(dataWrapper);
        return dataString.getBytes(StandardCharsets.UTF_8);
    }
}
