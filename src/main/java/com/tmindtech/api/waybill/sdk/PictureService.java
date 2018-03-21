package com.tmindtech.api.waybill.sdk;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

public interface PictureService {

    @GET("{path}")
    Call<ResponseBody> getOrderPictureByPath(@Path("path") String path);

}
