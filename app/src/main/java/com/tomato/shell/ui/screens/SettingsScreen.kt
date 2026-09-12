package com.tomato.shell.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tomato.shell.PageTitle
import com.tomato.shell.StatusCard
import com.tomato.shell.engine.EngineManager
import com.tomato.shell.ui.AppViewModel
import com.tomato.shell.ui.theme.GlassPanel

/**
 * 设置页：引擎状态大卡（点开自升级浮层）+ 引擎信息。
 * 对应设计稿 V1/V2 的 Clash Meta 主页列表语言。
 */
@Composable
fun SettingsScreen(
    vm: AppViewModel,
    onStatusClick: () -> Unit,
    engineReady: Boolean,
    engineStarting: Boolean,
    engineVersion: String,
    appVersion: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        PageTitle("设置")
        Spacer(Modifier.height(16.dp))

        // 引擎状态大卡：点开自升级浮层
        StatusCard(
            ready = engineReady,
            starting = engineStarting,
            version = engineVersion,
            onStatusClick = onStatusClick,
        )
        Spacer(Modifier.height(18.dp))

        // 引擎信息卡
        GlassPanel(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(15.dp)) {
                Text(text = "引擎", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                InfoRow("App 版本", appVersion)
                InfoRow("引擎版本", engineVersion.ifBlank { "未就绪" })
                InfoRow("引擎地址", EngineManager.baseUrl.replace("http://", ""))
                InfoRow("检查更新", "点上方状态卡")
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = "引擎升级会重启下载引擎，已下载的书不受影响。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(88.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}
