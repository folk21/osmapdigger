#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-.}"
cd "$ROOT"

if [[ ! -d mobile/shared || ! -d mobile/application || ! -d mobile/presentation ]]; then
  echo "Run from the osmapdigger repository root (or pass it as the first argument)." >&2
  exit 2
fi

declare -a SOURCES=(
  'mobile/shared/src/commonMain/kotlin/com/permieware/osmapdigger/analysis/PreferenceModels.kt'
  'mobile/shared/src/commonMain/kotlin/com/permieware/osmapdigger/analysis/PreferenceScorer.kt'
  'mobile/shared/src/commonMain/kotlin/com/permieware/osmapdigger/analysis/SettlementAnalysisModels.kt'
  'mobile/shared/src/commonMain/kotlin/com/permieware/osmapdigger/analysis/SettlementAnalysisService.kt'
  'mobile/shared/src/commonMain/kotlin/com/permieware/osmapdigger/analysis/SettlementCandidateScope.kt'
  'mobile/shared/src/commonMain/kotlin/com/permieware/osmapdigger/analysis/SettlementRanker.kt'
  'mobile/shared/src/commonMain/kotlin/com/permieware/osmapdigger/dataset/DatasetCandidateQueries.kt'
  'mobile/shared/src/commonMain/kotlin/com/permieware/osmapdigger/dataset/DatasetPackageContract.kt'
  'mobile/shared/src/commonMain/kotlin/com/permieware/osmapdigger/dataset/GeoRepository.kt'
  'mobile/shared/src/commonMain/kotlin/com/permieware/osmapdigger/dataset/OperationalGeoRepository.kt'
  'mobile/shared/src/commonMain/kotlin/com/permieware/osmapdigger/dataset/StableIdBatches.kt'
  'mobile/shared/src/commonMain/kotlin/com/permieware/osmapdigger/error/OperationalFailure.kt'
  'mobile/shared/src/commonMain/kotlin/com/permieware/osmapdigger/external/ExternalLinkOpener.kt'
  'mobile/shared/src/commonMain/kotlin/com/permieware/osmapdigger/external/ExternalSearchBatch.kt'
  'mobile/shared/src/commonMain/kotlin/com/permieware/osmapdigger/external/ExternalSearchProviders.kt'
  'mobile/shared/src/commonMain/kotlin/com/permieware/osmapdigger/external/OperationalExternalSearchProviderRepository.kt'
  'mobile/shared/src/commonMain/kotlin/com/permieware/osmapdigger/notebook/FavoriteAnalysisSnapshot.kt'
  'mobile/shared/src/commonMain/kotlin/com/permieware/osmapdigger/notebook/FavoriteAnalysisSnapshotCodec.kt'
  'mobile/shared/src/commonMain/kotlin/com/permieware/osmapdigger/notebook/FavoriteNotebookExport.kt'
  'mobile/shared/src/commonMain/kotlin/com/permieware/osmapdigger/notebook/FavoriteSettlement.kt'
  'mobile/shared/src/commonMain/kotlin/com/permieware/osmapdigger/preferences/OperationalUserPreferencesRepository.kt'
  'mobile/shared/src/commonMain/kotlin/com/permieware/osmapdigger/preferences/UserPreferences.kt'
  'mobile/shared/src/commonMain/kotlin/com/permieware/osmapdigger/presentation/DesktopWorkspaceLayoutPolicy.kt'
  'mobile/shared/src/commonMain/kotlin/com/permieware/osmapdigger/presentation/FilterSummaryBuilder.kt'
  'mobile/shared/src/commonMain/kotlin/com/permieware/osmapdigger/presentation/MetricDisplayNameResolver.kt'
  'mobile/shared/src/commonMain/kotlin/com/permieware/osmapdigger/presentation/MetricFilterPresentation.kt'
  'mobile/shared/src/commonMain/kotlin/com/permieware/osmapdigger/presentation/MetricValueFormatter.kt'
  'mobile/shared/src/commonMain/kotlin/com/permieware/osmapdigger/presentation/NumberFormatter.kt'
  'mobile/shared/src/commonMain/kotlin/com/permieware/osmapdigger/presentation/PreferenceDataAvailability.kt'
  'mobile/shared/src/commonMain/kotlin/com/permieware/osmapdigger/presentation/ScoreExplanationBuilder.kt'
  'mobile/shared/src/commonMain/kotlin/com/permieware/osmapdigger/presentation/SettlementCriteriaSummaryBuilder.kt'
  'mobile/shared/src/commonMain/kotlin/com/permieware/osmapdigger/presentation/SettlementDisplayNameResolver.kt'
  'mobile/shared/src/commonMain/kotlin/com/permieware/osmapdigger/presentation/SettlementPlaceTypeResolver.kt'
  'mobile/shared/src/commonMain/kotlin/com/permieware/osmapdigger/presentation/TwoRowLayout.kt'
  'mobile/shared/src/commonMain/kotlin/com/permieware/osmapdigger/presentation/UiLocalization.kt'
  'mobile/shared/src/commonMain/kotlin/com/permieware/osmapdigger/search/SearchInputParser.kt'
  'mobile/shared/src/commonMain/kotlin/com/permieware/osmapdigger/search/SearchRequestSemantics.kt'
  'mobile/shared/src/commonMain/kotlin/com/permieware/osmapdigger/search/SearchService.kt'
  'mobile/shared/src/commonMain/kotlin/com/permieware/osmapdigger/search/SettlementListImport.kt'
  'mobile/shared/src/commonMain/kotlin/com/permieware/osmapdigger/search/SettlementSearch.kt'
  'mobile/shared/src/commonMain/kotlin/com/permieware/osmapdigger/settings/ApplicationSettingsSchema.kt'
  'mobile/shared/src/commonMain/kotlin/com/permieware/osmapdigger/workspace/AnalysisWorkspaceController.kt'
  'mobile/shared/src/commonMain/kotlin/com/permieware/osmapdigger/workspace/FavoriteAnalysisSnapshotFactory.kt'
  'mobile/shared/src/commonMain/kotlin/com/permieware/osmapdigger/workspace/FavoriteCandidateTransfer.kt'
  'mobile/shared/src/commonTest/kotlin/com/permieware/osmapdigger/analysis/PreferenceScorerTest.kt'
  'mobile/shared/src/commonTest/kotlin/com/permieware/osmapdigger/analysis/SettlementAnalysisServiceTest.kt'
  'mobile/shared/src/commonTest/kotlin/com/permieware/osmapdigger/analysis/SettlementRankerTest.kt'
  'mobile/shared/src/commonTest/kotlin/com/permieware/osmapdigger/dataset/DatasetCandidateQueriesTest.kt'
  'mobile/shared/src/commonTest/kotlin/com/permieware/osmapdigger/dataset/DatasetPackageContractTest.kt'
  'mobile/shared/src/commonTest/kotlin/com/permieware/osmapdigger/dataset/StableIdBatchesTest.kt'
  'mobile/shared/src/commonTest/kotlin/com/permieware/osmapdigger/error/OperationalFailureTest.kt'
  'mobile/shared/src/commonTest/kotlin/com/permieware/osmapdigger/external/ExternalSearchProvidersTest.kt'
  'mobile/shared/src/commonTest/kotlin/com/permieware/osmapdigger/notebook/FavoriteAnalysisSnapshotCodecTest.kt'
  'mobile/shared/src/commonTest/kotlin/com/permieware/osmapdigger/notebook/FavoriteNotebookExportTest.kt'
  'mobile/shared/src/commonTest/kotlin/com/permieware/osmapdigger/preferences/UserPreferencesTest.kt'
  'mobile/shared/src/commonTest/kotlin/com/permieware/osmapdigger/presentation/DesktopWorkspaceLayoutPolicyTest.kt'
  'mobile/shared/src/commonTest/kotlin/com/permieware/osmapdigger/presentation/FilterSummaryBuilderTest.kt'
  'mobile/shared/src/commonTest/kotlin/com/permieware/osmapdigger/presentation/MetricDisplayNameResolverTest.kt'
  'mobile/shared/src/commonTest/kotlin/com/permieware/osmapdigger/presentation/MetricFilterPresentationBuilderTest.kt'
  'mobile/shared/src/commonTest/kotlin/com/permieware/osmapdigger/presentation/MetricValueFormatterTest.kt'
  'mobile/shared/src/commonTest/kotlin/com/permieware/osmapdigger/presentation/NumberFormatterTest.kt'
  'mobile/shared/src/commonTest/kotlin/com/permieware/osmapdigger/presentation/PreferenceDataAvailabilityBuilderTest.kt'
  'mobile/shared/src/commonTest/kotlin/com/permieware/osmapdigger/presentation/ScoreExplanationBuilderTest.kt'
  'mobile/shared/src/commonTest/kotlin/com/permieware/osmapdigger/presentation/SettlementCriteriaSummaryBuilderTest.kt'
  'mobile/shared/src/commonTest/kotlin/com/permieware/osmapdigger/presentation/SettlementDisplayNameResolverTest.kt'
  'mobile/shared/src/commonTest/kotlin/com/permieware/osmapdigger/presentation/SettlementPlaceTypeResolverTest.kt'
  'mobile/shared/src/commonTest/kotlin/com/permieware/osmapdigger/presentation/TwoRowLayoutTest.kt'
  'mobile/shared/src/commonTest/kotlin/com/permieware/osmapdigger/search/SearchInputParserTest.kt'
  'mobile/shared/src/commonTest/kotlin/com/permieware/osmapdigger/search/SearchServiceTest.kt'
  'mobile/shared/src/commonTest/kotlin/com/permieware/osmapdigger/search/SettlementListImportTest.kt'
  'mobile/shared/src/commonTest/kotlin/com/permieware/osmapdigger/search/SettlementSearchServiceTest.kt'
  'mobile/shared/src/commonTest/kotlin/com/permieware/osmapdigger/settings/ApplicationSettingsSchemaTest.kt'
  'mobile/shared/src/commonTest/kotlin/com/permieware/osmapdigger/workspace/AnalysisWorkspaceControllerTest.kt'
  'mobile/shared/src/commonTest/kotlin/com/permieware/osmapdigger/workspace/FavoriteAnalysisSnapshotFactoryTest.kt'
  'mobile/shared/src/commonTest/kotlin/com/permieware/osmapdigger/workspace/FavoriteCandidateTransferTest.kt'
  'mobile/shared/src/commonTest/kotlin/com/permieware/osmapdigger/workspace/SettlementShortlistWorkflowAcceptanceTest.kt'
)

missing_target=0
for src in "${SOURCES[@]}"; do
  rel="${src#mobile/shared/}"
  if [[ "$src" == *"/presentation/"* || "$src" == *"/workspace/SettlementShortlistWorkflowAcceptanceTest.kt" ]]; then
    dst="mobile/presentation/$rel"
  else
    dst="mobile/application/$rel"
  fi
  if [[ ! -f "$dst" ]]; then
    echo "Refusing to delete $src: moved target is missing: $dst" >&2
    missing_target=1
  fi
done

if [[ "$missing_target" -ne 0 ]]; then
  echo "Gate 7 move is incomplete; no files were deleted." >&2
  exit 1
fi

for src in "${SOURCES[@]}"; do
  rm -f -- "$src"
done

find mobile/shared/src/commonMain/kotlin/com/permieware/osmapdigger mobile/shared/src/commonTest/kotlin/com/permieware/osmapdigger -type d -empty -delete 2>/dev/null || true

echo "Gate 7 cleanup complete: removed ${#SOURCES[@]} moved source/test files from :shared."
echo "Next: make check && make check-all"
