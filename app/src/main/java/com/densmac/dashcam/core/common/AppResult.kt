package com.densmac.dashcam.core.common

sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(data))
    is AppResult.Failure -> this
}

inline fun <T> AppResult<T>.onSuccess(block: (T) -> Unit): AppResult<T> = also {
    if (it is AppResult.Success) block(it.data)
}

inline fun <T> AppResult<T>.onFailure(block: (AppError) -> Unit): AppResult<T> = also {
    if (it is AppResult.Failure) block(it.error)
}
