package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.AppRepository
import com.example.model.UserRole
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("الوسام", appName)
    }

    @Test
    fun `verify student STU004 login and role`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = AppRepository(context)

        val result = repository.loginWithCode("STU004")
        assertTrue(result.isSuccess)
        val user = result.getOrNull()
        assertNotNull(user)
        assertEquals(UserRole.STUDENT, user?.role)
        assertEquals("STU004", user?.code)
    }

    @Test
    fun `verify teacher TCH001 login and role`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = AppRepository(context)

        val result = repository.loginWithCode("TCH001")
        assertTrue(result.isSuccess)
        val user = result.getOrNull()
        assertNotNull(user)
        assertEquals(UserRole.TEACHER, user?.role)
    }

    @Test
    fun `verify parent PAR001 login and linked student codes`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = AppRepository(context)

        val result = repository.loginWithCode("PAR001")
        assertTrue(result.isSuccess)
        val user = result.getOrNull()
        assertNotNull(user)
        assertEquals(UserRole.PARENT, user?.role)
        assertTrue(user?.linkedStudentCodes?.contains("STU004") == true)
        assertTrue(user?.linkedStudentCodes?.contains("STU005") == true)
    }
}
