package com.sgr.app.network

import com.sgr.app.model.*
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // AUTH
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("api/auth/forgot-password")
    suspend fun forgotPassword(@Body body: Map<String, String>): Response<MessageResponse>

    // DASHBOARD
    @GET("api/dashboard/stats")
    suspend fun getDashboardStats(): Response<DashboardStats>

    // USERS
    @GET("api/users")
    suspend fun getUsers(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
        @Query("filter") filter: String = "",
        @Query("search") search: String = ""
    ): Response<PageResponse<User>>

    @GET("api/users/{id}")
    suspend fun getUser(@Path("id") id: Long): Response<User>

    @POST("api/users")
    suspend fun createUser(@Body request: CreateUserRequest): Response<ResponseBody>

    @PUT("api/users/{id}")
    suspend fun updateUser(@Path("id") id: Long, @Body request: UpdateUserRequest): Response<ResponseBody>

    @PATCH("api/users/{id}/toggle-status")
    suspend fun toggleUserStatus(@Path("id") id: Long): Response<ResponseBody>

    // SPACES
    @GET("api/spaces")
    suspend fun getSpaces(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
        @Query("filter") filter: String = "",
        @Query("search") search: String = ""
    ): Response<PageResponse<Space>>

    @GET("api/spaces/{id}")
    suspend fun getSpace(@Path("id") id: Long): Response<Space>

    @POST("api/spaces")
    suspend fun createSpace(@Body request: CreateSpaceRequest): Response<ResponseBody>

    @PUT("api/spaces/{id}")
    suspend fun updateSpace(@Path("id") id: Long, @Body request: CreateSpaceRequest): Response<ResponseBody>

    @PATCH("api/spaces/{id}/toggle-status")
    suspend fun toggleSpaceStatus(@Path("id") id: Long): Response<ResponseBody>

    @GET("api/reservations/by-space/{spaceId}")
    suspend fun getSpaceHistory(@Path("spaceId") id: Long): Response<List<Reservation>>

    @GET("api/equipments/by-space/{spaceId}")
    suspend fun getEquipmentsBySpace(@Path("spaceId") id: Long): Response<List<Equipment>>

    // EQUIPMENT
    @GET("api/equipments")
    suspend fun getEquipments(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
        @Query("filter") filter: String = "",
        @Query("search") search: String = ""
    ): Response<PageResponse<Equipment>>

    @GET("api/equipments/{id}")
    suspend fun getEquipment(@Path("id") id: Long): Response<Equipment>

    @POST("api/equipments")
    suspend fun createEquipment(@Body request: CreateEquipmentRequest): Response<ResponseBody>

    @PUT("api/equipments/{id}")
    suspend fun updateEquipment(@Path("id") id: Long, @Body request: UpdateEquipmentRequest): Response<ResponseBody>

    @PATCH("api/equipments/{id}/toggle-status")
    suspend fun toggleEquipmentStatus(@Path("id") id: Long): Response<ResponseBody>

    @GET("api/reservations/by-equipment/{equipmentId}")
    suspend fun getEquipmentHistory(@Path("equipmentId") id: Long): Response<List<Reservation>>

    // RESERVATIONS (admin)
    @GET("api/reservations")
    suspend fun getReservations(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
        @Query("filter") filter: String = ""
    ): Response<PageResponse<Reservation>>

    @GET("api/reservations/{id}")
    suspend fun getReservation(@Path("id") id: Long): Response<Reservation>

    @PATCH("api/reservations/{id}/approve")
    suspend fun approveReservation(@Path("id") id: Long, @Body body: ApproveRequest): Response<ResponseBody>

    @PATCH("api/reservations/{id}/reject")
    suspend fun rejectReservation(@Path("id") id: Long, @Body body: RejectRequest): Response<ResponseBody>

    @PATCH("api/reservations/{id}/return")
    suspend fun returnReservation(@Path("id") id: Long, @Body body: ReturnRequest): Response<ResponseBody>

    // RESERVATIONS (user)
    @GET("api/reservations/my")
    suspend fun getMyReservations(
        @Query("userId") userId: Long,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
        @Query("filter") filter: String = ""
    ): Response<PageResponse<Reservation>>

    @GET("api/reservations/my/{id}")
    suspend fun getMyReservation(
        @Path("id") id: Long,
        @Query("userId") userId: Long
    ): Response<Reservation>

    @POST("api/reservations")
    suspend fun createReservation(@Body request: CreateReservationRequest): Response<ResponseBody>

    @PUT("api/reservations/my/{id}")
    suspend fun updateMyReservation(
        @Path("id") id: Long,
        @Query("userId") userId: Long,
        @Body request: UpdateReservationRequest
    ): Response<ResponseBody>

    @PATCH("api/reservations/my/{id}/cancel")
    suspend fun cancelMyReservation(
        @Path("id") id: Long,
        @Query("userId") userId: Long
    ): Response<ResponseBody>

    // PROFILE
    @GET("api/users/profile/{userId}")
    suspend fun getProfile(@Path("userId") userId: Long): Response<User>

    @PUT("api/users/profile/{userId}")
    suspend fun updateProfile(
        @Path("userId") userId: Long,
        @Body request: UpdateProfileRequest
    ): Response<ResponseBody>

    @PUT("api/users/profile/{userId}/change-password")
    suspend fun changePassword(
        @Path("userId") userId: Long,
        @Body request: ChangePasswordRequest
    ): Response<ResponseBody>

    // AUDIT
    @GET("api/reservations/audit")
    suspend fun getAudit(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
        @Query("filter") filter: String = ""
    ): Response<PageResponse<Reservation>>
}
