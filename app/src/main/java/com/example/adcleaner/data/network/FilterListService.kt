package com.example.adcleaner.data.network

import retrofit2.http.GET
import retrofit2.http.Url

interface FilterListService {
    @GET
    suspend fun fetchList(@Url url: String): String
}
