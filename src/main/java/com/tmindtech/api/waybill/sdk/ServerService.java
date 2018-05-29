package com.tmindtech.api.waybill.sdk;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;

public interface ServerService {

    @GET("label-print/health")
    Call<ResponseBody> getBestHealthServer();

}
