sed -i 's/itemViewBinding.root.clicks(this) {/val isViewerMode = requireActivity().intent.getBooleanExtra("viewer_mode_extra_key", false)\n                if (isViewerMode) { itemViewBinding.appCb.visibility = android.view.View.GONE }\n                itemViewBinding.root.clicks(this) {\n                    if (isViewerMode) { requireActivity().openFile(java.io.File(data.first.sourceDir)); return@clicks }/' app/src/main/java/com/tans/tfiletransporter/ui/filetransport/MyAppsFragment.kt

sed -i 's/import com.tans.tuiutils.view.clicks/import com.tans.tuiutils.view.clicks\nimport com.tans.tfiletransporter.utils.openFile/' app/src/main/java/com/tans/tfiletransporter/ui/filetransport/MyAppsFragment.kt

