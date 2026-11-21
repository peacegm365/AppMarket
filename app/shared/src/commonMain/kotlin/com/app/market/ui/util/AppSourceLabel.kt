package com.app.market.ui.util

import androidx.compose.runtime.Composable
import com.app.market.domain.model.market.AppSource
import com.app.market.resources.Res
import com.app.market.resources.source_honor
import com.app.market.resources.source_huawei
import com.app.market.resources.source_oppo
import com.app.market.resources.source_samsung
import com.app.market.resources.source_taptap
import com.app.market.resources.source_vivo
import com.app.market.resources.source_wandoujia
import com.app.market.resources.source_xiaomi
import org.jetbrains.compose.resources.stringResource

@Composable
fun appSourceLabel(source: AppSource): String = when (source) {
    AppSource.XIAOMI -> stringResource(Res.string.source_xiaomi)
    AppSource.VIVO -> stringResource(Res.string.source_vivo)
    AppSource.WANDOUJIA -> stringResource(Res.string.source_wandoujia)
    AppSource.OPPO -> stringResource(Res.string.source_oppo)
    AppSource.SAMSUNG -> stringResource(Res.string.source_samsung)
    AppSource.HONOR -> stringResource(Res.string.source_honor)
    AppSource.HUAWEI -> stringResource(Res.string.source_huawei)
    AppSource.TAPTAP -> stringResource(Res.string.source_taptap)
    // TODO: source_coolapk 资源访问器未生成，先用字面量，后续补 i18n
    AppSource.GOOGLE -> "Google Play"
    AppSource.COOLAPK -> "酷安"
    AppSource.KUAIBAO -> "好游快爆"
    AppSource.APPCHINA -> "应用汇"
    AppSource.TENCENT -> "应用宝"
    AppSource.BOX7723 -> "7723游戏盒"
    AppSource.FDROID -> "F-Droid"
    AppSource.APKPURE -> "APKPure"
    AppSource.APTOIDE -> "Aptoide"
    AppSource.GITHUB -> "GitHub"
    AppSource.LSPOSED -> "LSPosed"
    AppSource.GITLAB -> "GitLab"
}
