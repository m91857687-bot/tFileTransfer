sed -i 's/val viewBinding = FileTransportActivityBinding.bind(contentView)/val viewBinding = FileTransportActivityBinding.bind(contentView)\n        val isViewerMode = intent.getBooleanExtra("viewer_mode_extra_key", false)\n        if (isViewerMode) {\n            viewBinding.floatingActionBt.visibility = android.view.View.GONE\n            viewBinding.toolBar.title = "My Files & Apps"\n        }/' app/src/main/java/com/tans/tfiletransporter/ui/filetransport/FileTransportActivity.kt

sed -i 's/this@FileTransportActivity.supportFragmentManager.loadingDialogSuspend/if (!isViewerMode) this@FileTransportActivity.supportFragmentManager.loadingDialogSuspend/' app/src/main/java/com/tans/tfiletransporter/ui/filetransport/FileTransportActivity.kt

sed -i 's/val d = NoOptionalDialog(/if (!isViewerMode) {\n                val d = NoOptionalDialog(/' app/src/main/java/com/tans/tfiletransporter/ui/filetransport/FileTransportActivity.kt

sed -i 's/finish()/finish()\n            }/' app/src/main/java/com/tans/tfiletransporter/ui/filetransport/FileTransportActivity.kt

