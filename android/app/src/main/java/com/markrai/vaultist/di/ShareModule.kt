package com.markrai.vaultist.di

import com.markrai.vaultist.data.share.DefaultNoteSharePreparer
import com.markrai.vaultist.data.share.NoteSharePreparer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ShareModule {
    @Binds
    @Singleton
    abstract fun bindNoteSharePreparer(preparer: DefaultNoteSharePreparer): NoteSharePreparer
}
