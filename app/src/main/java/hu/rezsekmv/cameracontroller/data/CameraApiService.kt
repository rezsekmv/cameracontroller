package hu.rezsekmv.cameracontroller.data

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Url

interface CameraApiService {
    @GET
    suspend fun getRequest(@Url url: String): Response<String>
}
