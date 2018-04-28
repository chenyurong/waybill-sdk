package com.tmindtech.api.waybill.sdk;

import com.tmindtech.api.waybill.sdk.model.ImageData;
import retrofit2.Call;
import retrofit2.http.Field;
import retrofit2.http.POST;

public interface PictureService {


    @POST
    Call<ImageData> getOrderPictureByPath(@Field("yx_message") String yxMessage);

}
