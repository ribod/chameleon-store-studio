package dev.ribod.storestudio.core.publish

import com.skydoves.sandwich.ApiResponse

internal fun <T> ApiResponse.Failure<*>.asFailure(): ApiResponse<T> = when (this) {
    is ApiResponse.Failure.Error -> ApiResponse.Failure.Error(payload)
    is ApiResponse.Failure.Exception -> ApiResponse.Failure.Exception(throwable)
}

internal fun <T, R> ApiResponse<T>.map(transform: (T) -> R): ApiResponse<R> = when (this) {
    is ApiResponse.Success -> ApiResponse.Success(transform(data), tag)
    is ApiResponse.Failure -> asFailure()
}
