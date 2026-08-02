// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.vifs.viroutefs.CardBlock
import dev.vifs.viroutefs.StatusChip
import dev.vifs.viroutefs.WarningText
import dev.vifs.viroutefs.routing.ProfileSubscription
import dev.vifs.viroutefs.routing.ProfileSubscriptionClient
import dev.vifs.viroutefs.routing.ProfileSubscriptionUpdatePreview
import dev.vifs.viroutefs.routing.RoutingConfig
import dev.vifs.viroutefs.routing.SubscriptionProfileChangeKind
import dev.vifs.viroutefs.routing.applyProfileSubscriptionUpdate
import dev.vifs.viroutefs.routing.maskSubscriptionUrl
import dev.vifs.viroutefs.routing.previewProfileSubscriptionImport
import dev.vifs.viroutefs.routing.previewProfileSubscriptionUpdate
import dev.vifs.viroutefs.routing.validateRoutingConfig
import dev.vifs.viroutefs.routing.validateSubscriptionUrlSyntax
import dev.vifs.viroutefs.routing.withSubscriptionEnabled
import dev.vifs.viroutefs.routing.withoutSubscription
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ProfileSubscriptionsCard(
    config: RoutingConfig,
    onConfig: (RoutingConfig, String?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val client = remember { ProfileSubscriptionClient() }
    var newName by rememberSaveable { mutableStateOf("") }
    var newUrl by remember { mutableStateOf("") }
    var loadingId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingPreview by remember { mutableStateOf<ProfileSubscriptionUpdatePreview?>(null) }
    var deleteConfirmationId by rememberSaveable { mutableStateOf<String?>(null) }

    fun fetchPreview(subscription: ProfileSubscription) {
        error = null
        pendingPreview = null
        loadingId = subscription.id
        scope.launch {
            runCatching {
                val fetched = withContext(Dispatchers.IO) {
                    client.fetch(subscription.url)
                }
                val imported = withContext(Dispatchers.Default) {
                    previewProfileSubscriptionImport(fetched.body)
                }
                previewProfileSubscriptionUpdate(
                    config = config,
                    subscription = subscription,
                    imported = imported,
                    fetchedAtEpochMs = fetched.fetchedAtEpochMs,
                )
            }.onSuccess { preview ->
                pendingPreview = preview
            }.onFailure { throwable ->
                error = throwable.message
                    ?: "Не удалось получить или проверить подписку."
            }
            loadingId = null
        }
    }

    CardBlock {
        Text("Подписки на профили", fontWeight = FontWeight.SemiBold)
        Text(
            "Только ручное обновление по HTTPS. Перед применением показываются изменения; новые серверы остаются выключенными.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (config.subscriptions.isEmpty()) {
            Text(
                "Сохранённых подписок пока нет.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            config.subscriptions.forEach { subscription ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(subscription.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                maskSubscriptionUrl(subscription.url),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                subscription.lastUpdatedAtEpochMs?.let {
                                    "Последнее обновление: ${
                                        DateFormat.getDateTimeInstance(
                                            DateFormat.SHORT,
                                            DateFormat.SHORT,
                                        ).format(Date(it))
                                    } • профилей: ${subscription.lastProfileCount}"
                                } ?: "Ещё не обновлялась.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        StatusChip(if (subscription.enabled) "Включена" else "Отключена")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { fetchPreview(subscription) },
                            enabled = loadingId == null && subscription.enabled,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (loadingId == subscription.id) "Загрузка…" else "Проверить")
                        }
                        FilterChip(
                            selected = subscription.enabled,
                            onClick = {
                                onConfig(
                                    config.withSubscriptionEnabled(
                                        subscription.id,
                                        !subscription.enabled,
                                    ),
                                    if (subscription.enabled) {
                                        "Подписка отключена. Существующие профили и маршруты сохранены."
                                    } else {
                                        "Подписка включена. Обновление остаётся только ручным."
                                    },
                                )
                            },
                            enabled = loadingId == null,
                            label = { Text(if (subscription.enabled) "Отключить" else "Включить") },
                        )
                    }
                    if (deleteConfirmationId == subscription.id) {
                        WarningText(
                            "Удалить подписку? Её профили останутся на месте, будут выключены и отвязаны; пользовательские маршруты сохранят свои цели.",
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = {
                                    onConfig(
                                        config.withoutSubscription(subscription.id),
                                        "Подписка удалена. Её профили сохранены выключенными, маршруты не удалены.",
                                    )
                                    deleteConfirmationId = null
                                    pendingPreview = null
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Удалить")
                            }
                            OutlinedButton(
                                onClick = { deleteConfirmationId = null },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Отмена")
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { deleteConfirmationId = subscription.id },
                            enabled = loadingId == null,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Удалить подписку")
                        }
                    }
                }
            }
        }

        Text("Добавить подписку", fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = newName,
            onValueChange = {
                newName = it
                error = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Понятное имя") },
            singleLine = true,
        )
        OutlinedTextField(
            value = newUrl,
            onValueChange = {
                newUrl = it
                error = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("HTTPS URL (скрыт)") },
            placeholder = { Text("https://provider.example/…") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        Button(
            onClick = {
                val name = newName.trim()
                val url = newUrl.trim()
                when {
                    name.isBlank() -> error = "Укажите понятное имя подписки."
                    name.length > 120 -> error = "Имя подписки слишком длинное."
                    config.subscriptions.size >= 50 -> error = "Можно сохранить не более 50 подписок."
                    validateSubscriptionUrlSyntax(url) != null ->
                        error = validateSubscriptionUrlSyntax(url)
                    else -> fetchPreview(
                        ProfileSubscription(
                            id = "subscription_${UUID.randomUUID()}",
                            name = name,
                            url = url,
                        ),
                    )
                }
            },
            enabled = loadingId == null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (loadingId != null) "Загрузка…" else "Загрузить и показать изменения")
        }
        Text(
            "Полный URL может содержать токен: он хранится отдельно в зашифрованном хранилище Android Keystore и не попадает в диагностический JSON.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        error?.let { WarningText(it) }

        pendingPreview?.let { preview ->
            SubscriptionUpdatePreviewBlock(
                preview = preview,
                onApply = {
                    val next = applyProfileSubscriptionUpdate(config, preview)
                    val validationErrors = validateRoutingConfig(next)
                    if (validationErrors.isNotEmpty()) {
                        error = validationErrors.joinToString("\n")
                    } else {
                        onConfig(
                            next,
                            "Подписка применена: новых ${preview.addedCount}, обновлено ${preview.updatedCount}, без изменений ${preview.unchangedCount}, отсутствуют ${preview.removedProfiles.size}. Новые профили выключены.",
                        )
                        if (config.subscriptions.none { it.id == preview.subscription.id }) {
                            newName = ""
                            newUrl = ""
                        }
                        pendingPreview = null
                        error = null
                    }
                },
                onCancel = { pendingPreview = null },
            )
        }
    }
}

@Composable
private fun SubscriptionUpdatePreviewBlock(
    preview: ProfileSubscriptionUpdatePreview,
    onApply: () -> Unit,
    onCancel: () -> Unit,
) {
    Text("Предпросмотр «${preview.subscription.name}»", fontWeight = FontWeight.SemiBold)
    Text(
        "Новые: ${preview.addedCount} • изменены: ${preview.updatedCount} • без изменений: ${preview.unchangedCount} • исчезли из списка: ${preview.removedProfiles.size}",
        style = MaterialTheme.typography.bodySmall,
    )
    preview.changes.take(MAX_VISIBLE_SUBSCRIPTION_CHANGES).forEach { change ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(change.profile.name, fontWeight = FontWeight.SemiBold)
                StatusChip(
                    when (change.kind) {
                        SubscriptionProfileChangeKind.Added -> "Новый • выкл"
                        SubscriptionProfileChangeKind.Updated ->
                            if (change.profile.enabled) "Изменён • вкл" else "Изменён • выкл"
                        SubscriptionProfileChangeKind.Unchanged ->
                            if (change.profile.enabled) "Без изменений • вкл" else "Без изменений • выкл"
                    },
                )
            }
            Text(change.maskedPreview, style = MaterialTheme.typography.bodySmall)
            change.warnings.forEach { WarningText(it) }
        }
    }
    if (preview.changes.size > MAX_VISIBLE_SUBSCRIPTION_CHANGES) {
        Text(
            "Ещё ${preview.changes.size - MAX_VISIBLE_SUBSCRIPTION_CHANGES} профилей скрыты из короткого списка; они учтены в итоговых числах.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    preview.removedProfiles.take(MAX_VISIBLE_SUBSCRIPTION_CHANGES).forEach { profile ->
        WarningText(
            "«${profile.name}» исчез из списка: после применения останется в приложении, но будет выключен.",
        )
    }
    if (preview.removedProfiles.size > MAX_VISIBLE_SUBSCRIPTION_CHANGES) {
        WarningText(
            "Ещё ${preview.removedProfiles.size - MAX_VISIBLE_SUBSCRIPTION_CHANGES} исчезнувших профилей скрыты из короткого списка; все они останутся выключенными.",
        )
    }
    preview.warnings.take(MAX_VISIBLE_SUBSCRIPTION_WARNINGS).forEach { WarningText(it) }
    if (preview.warnings.size > MAX_VISIBLE_SUBSCRIPTION_WARNINGS) {
        WarningText(
            "Ещё ${preview.warnings.size - MAX_VISIBLE_SUBSCRIPTION_WARNINGS} замечаний скрыты. Исправьте формат списка у провайдера, если число распознанных профилей неожиданно.",
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = onApply, modifier = Modifier.weight(1f)) {
            Text("Применить")
        }
        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
            Text("Отмена")
        }
    }
}

private const val MAX_VISIBLE_SUBSCRIPTION_CHANGES = 40
private const val MAX_VISIBLE_SUBSCRIPTION_WARNINGS = 20
