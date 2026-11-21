package com.app.market.domain.repository

import com.app.market.domain.model.update.SelfUpdate

/** 查询应用自身的最新发布；Android 实现对接 GitLab，桌面端返回空。 */
interface SelfUpdateRepository {
    /** 发布页地址，供浏览器打开。 */
    val projectUrl: String

    /** 下载发布资产所需的身份请求头。 */
    val authHeaders: Map<String, String>

    suspend fun latestRelease(): SelfUpdate?
}