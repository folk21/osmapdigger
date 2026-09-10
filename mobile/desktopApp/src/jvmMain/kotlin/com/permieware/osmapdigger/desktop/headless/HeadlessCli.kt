package com.permieware.osmapdigger.desktop.headless

import com.permieware.osmapdigger.domain.SearchCondition
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlinx.coroutines.runBlocking

/** Developer-only CLI adapter over [DesktopHeadlessWorkspace]. */
fun main(args: Array<String>) = runBlocking {
    val parsed = HeadlessCliArguments.parse(args.toList())
    DesktopHeadlessWorkspace.open(parsed.dataset, parsed.settings).use { workspace ->
        workspace.initialize()
        when (val command = parsed.command) {
            HeadlessCommand.State -> printState(workspace)
            is HeadlessCommand.Import -> {
                val result = workspace.importCandidates(Files.readString(command.file))
                if (!result.applied) {
                    System.err.println("Import was not applied: ${result.unresolved.size} row(s) require review")
                    result.unresolved.forEach { System.err.println("- ${it.line.sourceText}: ${it::class.simpleName}") }
                    error("Imported candidate list requires explicit review")
                }
                println("Imported ${result.resolvedSettlementIds.size} settlement(s)")
            }
            is HeadlessCommand.Required -> {
                workspace.setRequiredCondition(SearchCondition(command.metricId, command.min, command.max))
                printState(workspace)
            }
            is HeadlessCommand.RequiredRemove -> {
                workspace.removeRequiredCondition(command.metricId)
                printState(workspace)
            }
            is HeadlessCommand.Preference -> {
                workspace.setPreferenceEnabled(command.metricId, command.enabled)
                printState(workspace)
            }
            is HeadlessCommand.PreferenceWeight -> {
                workspace.setPreferenceWeight(command.metricId, command.weight)
                printState(workspace)
            }
            is HeadlessCommand.PreferenceThresholds -> {
                workspace.setPreferenceThresholds(command.metricId, command.target, command.limit)
                printState(workspace)
            }
            is HeadlessCommand.PreferenceReset -> {
                workspace.resetPreference(command.metricId)
                printState(workspace)
            }
            HeadlessCommand.ImportDeactivate -> { workspace.deactivateImportedCandidates(); printState(workspace) }
            HeadlessCommand.ImportActivate -> { workspace.activateImportedCandidates(); printState(workspace) }
            HeadlessCommand.ImportClear -> { workspace.clearImportedCandidates(); printState(workspace) }
            is HeadlessCommand.Center -> {
                workspace.setCenter(command.settlementId)
                printState(workspace)
            }
            is HeadlessCommand.Radius -> {
                workspace.setRadiusKm(command.radiusKm)
                printState(workspace)
            }
            is HeadlessCommand.FavoriteAdd -> {
                workspace.addFavorite(command.settlementId)
                printState(workspace)
            }
            is HeadlessCommand.FavoriteNote -> {
                workspace.updateFavoriteNote(command.settlementId, command.note)
                printState(workspace)
            }
            is HeadlessCommand.FavoriteSnapshot -> {
                workspace.updateFavoriteSnapshot(command.settlementId)
                printState(workspace)
            }
            is HeadlessCommand.FavoriteRemove -> { workspace.removeFavorite(command.settlementId); printState(workspace) }
            HeadlessCommand.FavoritesClear -> { workspace.clearFavorites(); printState(workspace) }
            is HeadlessCommand.FavoritesToImport -> {
                val imported = workspace.copyFavoritesToImported(command.settlementIds)
                println("Imported from Favorites: ${imported.settlementIds.joinToString(",")}")
            }
            is HeadlessCommand.BatchSearch -> {
                workspace.buildBatchSearch(command.providerId, command.settlementIds)
                    .forEach { println(it.url) }
            }
            is HeadlessCommand.Export -> {
                workspace.exportFavorites(command.destination)
                println(command.destination.toAbsolutePath())
            }
        }
    }
}

private suspend fun printState(workspace: DesktopHeadlessWorkspace) {
    val state = workspace.state()
    println("dataset=${state.datasetInfo?.id}")
    println("candidateScope=${state.candidateScope}")
    println("required=${state.conditions.joinToString { it.metricId }}")
    println("preferences=${state.effectivePreferences.filter { it.enabled }.joinToString { it.metricId }}")
    println("ranked=${state.rankedResults.joinToString { "${it.settlement.id}:${it.score.value ?: "n/a"}" }}")
    println("favorites=${state.favorites.joinToString { it.settlementId }}")
}

private data class HeadlessCliArguments(
    val dataset: Path,
    val settings: Path,
    val command: HeadlessCommand,
) {
    companion object {
        fun parse(args: List<String>): HeadlessCliArguments {
            fun option(name: String): String {
                val index = args.indexOf(name)
                require(index >= 0 && index + 1 < args.size) { "Missing required option $name" }
                return args[index + 1]
            }
            val dataset = Path(option("--dataset"))
            val settings = Path(option("--settings"))
            val commandTokens = buildList {
                var index = 0
                while (index < args.size) {
                    when (args[index]) {
                        "--dataset", "--settings" -> index += 2
                        else -> {
                            addAll(args.drop(index))
                            break
                        }
                    }
                }
            }
            require(commandTokens.isNotEmpty()) { usage() }
            return HeadlessCliArguments(dataset, settings, HeadlessCommand.parse(commandTokens))
        }

        private fun usage(): String =
            "Usage: --dataset <dir> --settings <sqlite> <state|import|import-activate|import-deactivate|import-clear|required|required-remove|preference|preference-weight|preference-thresholds|preference-reset|center|radius|favorite-add|favorite-note|favorite-snapshot|favorite-remove|favorites-clear|favorites-to-import|batch-search|export> ..."
    }
}

private sealed interface HeadlessCommand {
    data object State : HeadlessCommand
    data class Import(val file: Path) : HeadlessCommand
    data object ImportActivate : HeadlessCommand
    data object ImportDeactivate : HeadlessCommand
    data object ImportClear : HeadlessCommand
    data class Required(val metricId: String, val min: Double?, val max: Double?) : HeadlessCommand
    data class RequiredRemove(val metricId: String) : HeadlessCommand
    data class Preference(val metricId: String, val enabled: Boolean) : HeadlessCommand
    data class PreferenceWeight(val metricId: String, val weight: Int) : HeadlessCommand
    data class PreferenceThresholds(val metricId: String, val target: Double, val limit: Double) : HeadlessCommand
    data class PreferenceReset(val metricId: String) : HeadlessCommand
    data class Center(val settlementId: String?) : HeadlessCommand
    data class Radius(val radiusKm: Double?) : HeadlessCommand
    data class FavoriteAdd(val settlementId: String) : HeadlessCommand
    data class FavoriteNote(val settlementId: String, val note: String?) : HeadlessCommand
    data class FavoriteSnapshot(val settlementId: String) : HeadlessCommand
    data class FavoriteRemove(val settlementId: String) : HeadlessCommand
    data object FavoritesClear : HeadlessCommand
    data class FavoritesToImport(val settlementIds: Set<String>) : HeadlessCommand
    data class BatchSearch(val providerId: String, val settlementIds: Set<String>) : HeadlessCommand
    data class Export(val destination: Path) : HeadlessCommand

    companion object {
        fun parse(tokens: List<String>): HeadlessCommand = when (tokens.first()) {
            "state" -> State
            "import" -> Import(Path(tokens.requireArg(1)))
            "import-activate" -> ImportActivate
            "import-deactivate" -> ImportDeactivate
            "import-clear" -> ImportClear
            "required" -> Required(tokens.requireArg(1), tokens.nullableDouble(2), tokens.nullableDouble(3))
            "required-remove" -> RequiredRemove(tokens.requireArg(1))
            "preference" -> Preference(tokens.requireArg(1), tokens.requireArg(2).toBooleanStrict())
            "preference-weight" -> PreferenceWeight(tokens.requireArg(1), tokens.requireArg(2).toInt())
            "preference-thresholds" -> PreferenceThresholds(tokens.requireArg(1), tokens.requireArg(2).toDouble(), tokens.requireArg(3).toDouble())
            "preference-reset" -> PreferenceReset(tokens.requireArg(1))
            "center" -> Center(tokens.requireArg(1).takeUnless { it == "-" })
            "radius" -> Radius(tokens.requireArg(1).takeUnless { it == "-" }?.toDouble())
            "favorite-add" -> FavoriteAdd(tokens.requireArg(1))
            "favorite-note" -> FavoriteNote(tokens.requireArg(1), tokens.drop(2).joinToString(" ").takeIf { it.isNotBlank() && it != "-" })
            "favorite-snapshot" -> FavoriteSnapshot(tokens.requireArg(1))
            "favorite-remove" -> FavoriteRemove(tokens.requireArg(1))
            "favorites-clear" -> FavoritesClear
            "favorites-to-import" -> FavoritesToImport(tokens.requireArg(1).split(',').filter(String::isNotBlank).toSet())
            "batch-search" -> BatchSearch(tokens.requireArg(1), tokens.requireArg(2).split(',').filter(String::isNotBlank).toSet())
            "export" -> Export(Path(tokens.requireArg(1)))
            else -> error("Unknown headless command '${tokens.first()}'")
        }
    }
}

private fun List<String>.requireArg(index: Int): String = getOrNull(index) ?: error("Missing command argument #$index")
private fun List<String>.nullableDouble(index: Int): Double? = requireArg(index).takeUnless { it == "-" }?.toDouble()
