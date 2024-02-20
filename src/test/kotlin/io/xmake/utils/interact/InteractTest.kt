package io.xmake.utils.interact

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.mockk.every
import io.mockk.junit4.MockKRule
import io.mockk.mockkStatic
import io.xmake.TestData
import io.xmake.utils.ioRunv
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class XMakeVersion:BasePlatformTestCase(){
    val xmakeVersion = TestData.xmakeVersion

    @get:Rule
    val mockkRule = MockKRule(this)

    @Before
    fun before() {
        mockkStatic(::ioRunv)
        every { ioRunv(any()) } returns xmakeVersion.split("\n")
    }

    @Test
    fun `Test kXMakeFind`() {
        val testCase: Boolean = true
        val result: Boolean = kXMakeFind
        assertEquals(testCase, result)
    }

    @Test
    fun `Test kXMakeVersion`() {
        val testCase: String = "2.8.6 HEAD.211710b67"
        val result: String = kXMakeVersion
        assertEquals(testCase, result)
    }

}

@RunWith(JUnit4::class)
class XMakeFH:BasePlatformTestCase() {
    val xmakeFHelp = TestData.xmakeFHelp

    @get:Rule
    val mockkRule = MockKRule(this)

    @Before
    fun before() {
        mockkStatic(::ioRunv)
        every { ioRunv(any()) } returns xmakeFHelp.split("\n")
    }

    @Test
    fun `Test kPlatList`() {
        val testCase: List<String> = listOf(
            "android",
            "appletvos",
            "applexros",
            "bsd",
            "cross",
            "cygwin",
            "haiku",
            "iphoneos",
            "linux",
            "macosx",
            "mingw",
            "msys",
            "wasm",
            "watchos",
            "windows",
        )
        val result: List<String> = kPlatList
        assertEquals(testCase, result)
    }

    @Test
    fun `Test kPlatArchMap`(){
        val testCase: Map<String, List<String>> = mapOf(
            "android"   to "armeabi armeabi-v7a arm64-v8a x86 x86_64 mips mip64".split(' '),
            "appletvos" to "arm64 armv7 armv7s i386 x86_64".split(' '),
            "applexros" to "arm64 armv7 armv7s i386 x86_64".split(' '),
            "bsd"       to "i386 x86_64".split(' '),
            "cross"     to "i386 x86_64 arm arm64 mips mips64 riscv riscv64 s390x ppc ppc64 sh4".split(' '),
            "cygwin"    to "i386 x86_64".split(' '),
            "haiku"     to "i386 x86_64".split(' '),
            "iphoneos"  to "arm64 x86_64".split(' '),
            "linux"     to "i386 x86_64 armv7 armv7s arm64-v8a mips mips64 mipsel mips64el loongarch64".split(' '),
            "macosx"    to "x86_64 arm64".split(' '),
            "mingw"     to "i386 x86_64 arm arm64".split(' '),
            "msys"      to "i386 x86_64".split(' '),
            "wasm"      to "wasm32 wasm64".split(' '),
            "watchos"   to "armv7k i386".split(' '),
            "windows"   to "x86 x64 arm64".split(' '),
        )
        val result: Map<String, List<String>> = kPlatArchMap
        assertEquals(testCase, result)
    }
}