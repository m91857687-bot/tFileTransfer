sed -i 's/override fun getItemCount(): Int = fragments.size/override fun getItemCount(): Int = if (isViewerMode) 5 else fragments.size/' app/src/main/java/com/tans/tfiletransporter/ui/filetransport/FileTransportActivity.kt

sed -i 's/viewBinding.viewPager.offscreenPageLimit = fragments.size/viewBinding.viewPager.offscreenPageLimit = if (isViewerMode) 5 else fragments.size/' app/src/main/java/com/tans/tfiletransporter/ui/filetransport/FileTransportActivity.kt

sed -i 's/val (remoteInfo, remoteAddress) = with(intent) { getRemoteInfo() to getRemoteAddress() }/if (!isViewerMode) {\n                val (remoteInfo, remoteAddress) = with(intent) { getRemoteInfo() to getRemoteAddress() }\n                viewBinding.toolBar.title = remoteInfo\n                viewBinding.toolBar.subtitle = remoteAddress.hostAddress\n            }/' app/src/main/java/com/tans/tfiletransporter/ui/filetransport/FileTransportActivity.kt

sed -i '/viewBinding.toolBar.title = remoteInfo/d' app/src/main/java/com/tans/tfiletransporter/ui/filetransport/FileTransportActivity.kt
sed -i '/viewBinding.toolBar.subtitle = remoteAddress.hostAddress/d' app/src/main/java/com/tans/tfiletransporter/ui/filetransport/FileTransportActivity.kt

