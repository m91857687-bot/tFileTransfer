sed -i '105,185s/itemViewBinding.imageCb/itemViewBinding.mediaCb/g' app/src/main/java/com/tans/tfiletransporter/ui/filetransport/BaseMediaFragment.kt
sed -i 's/import com.tans.tuiutils.view.clicks/import com.tans.tuiutils.view.clicks\nimport com.tans.tfiletransporter.utils.openFile/' app/src/main/java/com/tans/tfiletransporter/ui/filetransport/BaseMediaFragment.kt
