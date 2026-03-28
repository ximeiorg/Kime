package com.kingzcheung.kime.rime

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Rime 配置助手类
 * 负责初始化 Rime 数据目录和配置文件
 * 
 * 支持两种方式提供配置文件：
 * 1. 从 assets/rime 目录复制配置文件（推荐）
 * 2. 使用内置的默认配置
 */
object RimeConfigHelper {
    private const val TAG = "RimeConfigHelper"
    private const val ASSETS_RIME_DIR = "rime"
    
    /**
     * 初始化 Rime 数据目录
     * 创建必要的目录结构并写入默认配置
     */
    fun initializeRimeData(context: Context): Pair<String, String> {
        val sharedDataDir = File(context.filesDir, "rime/shared")
        val userDataDir = File(context.filesDir, "rime/user")
        
        Log.d(TAG, "initializeRimeData: sharedDataDir=${sharedDataDir.absolutePath}")
        Log.d(TAG, "initializeRimeData: userDataDir=${userDataDir.absolutePath}")
        
        // 创建目录
        if (!sharedDataDir.exists()) {
            val created = sharedDataDir.mkdirs()
            Log.d(TAG, "Created sharedDataDir: $created")
        }
        if (!userDataDir.exists()) {
            val created = userDataDir.mkdirs()
            Log.d(TAG, "Created userDataDir: $created")
        }
        
        // 尝试从 assets 复制配置文件
        val copiedFromAssets = copyAssetsToRimeDir(context, sharedDataDir)
        
        // 如果没有从 assets 复制到文件，则使用默认配置
        if (!copiedFromAssets) {
            Log.d(TAG, "No assets found, using default config")
            writeDefaultConfig(sharedDataDir, userDataDir)
        }
        
        // 列出 sharedDataDir 中的文件，用于调试
        val files = sharedDataDir.listFiles()
        if (files != null) {
            Log.d(TAG, "Files in sharedDataDir (${files.size} files):")
            for (file in files) {
                Log.d(TAG, "  - ${file.name} (${file.length()} bytes)")
            }
        } else {
            Log.e(TAG, "sharedDataDir is empty or not a directory!")
        }
        
        return Pair(userDataDir.absolutePath, sharedDataDir.absolutePath)
    }
    
    /**
     * 从 assets 目录复制 Rime 配置文件
     * @return 是否成功复制了有效的配置文件
     */
    private fun copyAssetsToRimeDir(context: Context, targetDir: File): Boolean {
        try {
            val assetManager = context.assets
            val files = assetManager.list(ASSETS_RIME_DIR)
            
            if (files.isNullOrEmpty()) {
                Log.d(TAG, "No files found in assets/$ASSETS_RIME_DIR")
                return false
            }
            
            // 只复制 .yaml 文件（排除 README.md 等非配置文件）
            val yamlFiles = files.filter { it.endsWith(".yaml") }
            if (yamlFiles.isEmpty()) {
                Log.d(TAG, "No .yaml files found in assets/$ASSETS_RIME_DIR")
                return false
            }
            
            Log.d(TAG, "Found ${yamlFiles.size} .yaml files in assets/$ASSETS_RIME_DIR")
            
            for (fileName in yamlFiles) {
                copyAssetFile(context, "$ASSETS_RIME_DIR/$fileName", File(targetDir, fileName))
            }
            
            return true
        } catch (e: IOException) {
            Log.e(TAG, "Failed to copy assets", e)
            return false
        }
    }
    
    /**
     * 复制单个 asset 文件
     * 强制覆盖已存在的文件，确保使用最新的配置
     */
    private fun copyAssetFile(context: Context, assetPath: String, targetFile: File) {
        try {
            // 强制覆盖配置文件
            if (targetFile.exists()) {
                Log.d(TAG, "Overwriting existing file: ${targetFile.name}")
                targetFile.delete()
            }
            
            context.assets.open(assetPath).use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            Log.d(TAG, "Copied: $assetPath -> ${targetFile.absolutePath}")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to copy: $assetPath", e)
        }
    }
    
    /**
     * 写入默认配置文件（当没有 assets 时使用）
     */
    private fun writeDefaultConfig(sharedDataDir: File, userDataDir: File) {
        // 写入 default.yaml 到 shared 目录
        writeToFile(
            File(sharedDataDir, "default.yaml"),
            getDefaultYaml()
        )
        
        // 写入 wubi86.schema.yaml 到 shared 目录
        writeToFile(
            File(sharedDataDir, "wubi86.schema.yaml"),
            getWubi86SchemaYaml()
        )
        
        // 写入 wubi86.dict.yaml 到 shared 目录
        writeToFile(
            File(sharedDataDir, "wubi86.dict.yaml"),
            getWubi86DictYaml()
        )
        
        // 写入用户配置到 user 目录
        writeToFile(
            File(userDataDir, "default.custom.yaml"),
            getDefaultCustomYaml()
        )
        
        Log.d(TAG, "Default config files written successfully")
    }
    
    /**
     * 写入文件
     */
    private fun writeToFile(file: File, content: String) {
        try {
            if (!file.exists()) {
                FileOutputStream(file).use { output ->
                    output.write(content.toByteArray(Charsets.UTF_8))
                }
                Log.d(TAG, "Written: ${file.absolutePath}")
            } else {
                Log.d(TAG, "File already exists: ${file.absolutePath}")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to write file: ${file.absolutePath}", e)
        }
    }
    
    /**
     * 默认配置
     */
    private fun getDefaultYaml(): String {
        return """
# Rime 默认配置
schema_list:
  - schema: wubi86

switcher:
  caption: "方案选单"
  hotkeys:
    - "Control+grave"
    - "Control+Shift+grave"

menu:
  page_size: 5

punctuator:
  import_preset: default

key_binder:
  import_preset: default

recognizer:
  import_preset: default
""".trimIndent()
    }
    
    /**
     * 五笔86输入方案
     */
    private fun getWubi86SchemaYaml(): String {
        return """
# 五笔86输入方案
schema:
  schema_id: wubi86
  name: 五笔86
  version: "1.0.0"
  author:
    - "Kime"
  description: |
    五笔字型输入法
    86版

switches:
  - name: ascii_mode
    reset: 0
    states: [ 中文, 西文 ]
  - name: full_shape
    states: [ 半角, 全角 ]
  - name: simplification
    reset: 1
    states: [ 漢字, 汉字 ]
  - name: extended_charset
    reset: 0
    states: [ 常用, 扩展 ]

engine:
  processors:
    - ascii_composer
    - recognizer
    - key_binder
    - speller
    - punctuator
    - selector
    - navigator
    - express_editor
  segmentors:
    - ascii_segmentor
    - matcher
    - abc_segmentor
    - punct_segmentor
    - fallback_segmentor
  translators:
    - punct_translator
    - reverse_lookup_translator
    - table_translator
  filters:
    - simplifier
    - uniquifier

speller:
  alphabet: "abcdefghijklmnopqrstuvwxyz"
  delimiter: " '"
  max_code_length: 4
  auto_select: true
  auto_select_unique_candidate: true

translator:
  dictionary: wubi86
  prism: wubi86
  enable_charset_filter: true
  enable_encoder: true
  enable_sentence: false
  enable_completion: true
  preedit_format:
    - "xlit|hspnz|一丨丿丶乙|"
  comment_format:
    - "xlit|hspnz|一丨丿丶乙|"

reverse_lookup:
  dictionary: pinyin_simp
  prefix: "z"
  suffix: "'"
  tips: "〔拼音〕"
  preedit_format:
    - "xlit|v|ü|"
  comment_format:
    - "xlit|hspnz|一丨丿丶乙|"

punctuator:
  import_preset: default

key_binder:
  import_preset: default
  bindings:
    - { when: has_menu, accept: space, send: 1 }
    - { when: has_menu, accept: Release+space, send: 1 }
    - { when: composing, accept: space, send: 1 }
    - { when: composing, accept: Release+space, send: 1 }

menu:
  page_size: 5
""".trimIndent()
    }
    
    /**
     * 五笔86词库（基础词库）
     */
    private fun getWubi86DictYaml(): String {
        return """
# 五笔86词库
---
name: wubi86
version: "1.0.0"
sort: original
use_preset_vocabulary: false
...

# 常用字
一	g
二	fg
三	dg
四	lh
五	gg
六	uy
七	ag
八	wt
九	vt
十	fgh
百	dj
千	tf
万	dn
亿	wn
个	wh
位	wug
作	wthf
你	wq
他	wb
她	vb
它	px
我	q
们	wu
的	r
是	j
有	e
在	d
不	i
了	b
和	t
大	dd
这	p
主	yg
为	o
民	n
以	c
我	q
要	sv
他	wb
们	wu
这	p
中	k
国	l
人	w
年	rh
会	wf
地	fb
能	ce
说	yu
对	cf
生	tg
而	dm
子	bb
自	th
之	pp
去	fcu
来	go
分	wv
都	ftjb
好	vb
小	ih
多	qq
大	dd
上	h
下	gh
左	da
右	dk
东	ai
南	fm
西	sghg
北	ux
中	k
内	mw
外	qh
前	ue
后	rg
左	da
右	dk
天	gd
地	fb
人	w
事	gk
物	tr
时	jf
候	whn
候	whn
所	rn
以	c
可	sk
以	c
之	pp
者	ftj
也	bn
矣	ctd
焉	ghg
哉	fak
乎	tuh
于	gf
而	dmj
其	adw
或	ak
且	eg
若	adkj
则	mj
因	ld
故	dty
此	hx
彼	thc
何	wsk
怎	thfn
哪	kvfb
哪	kvfb
那	nftb
这	p
每	tx
各	tk
全	wg
多	qq
少	itr
几	mt
半	uf
双	cc
单	ujfj
双	cc
只	kw
仅	wcy
共	aw
同	m
与	gn
和	t
或	ak
而	dmj
且	eg
但	wjg
如	vk
果	js
然	qd
若	adkj
虽	kj
则	mj
因	ld
为	o
所	rn
以	c
故	dty
此	hx
彼	thc
何	wsk
怎	thfn
哪	kvfb
那	nftb
这	p
每	tx
各	tk
全	wg
都	ftjb
总	ukn
共	aw
同	m
与	gn
和	t
或	ak
而	dmj
且	eg
但	wjg
如	vk
果	js
然	qd
若	adkj
虽	kj
则	mj
因	ld
为	o
所	rn
以	c
故	dty

# 常用词组
中国	khlg
人民	wwna
国家	lgpe
发展	ntna
经济	xci
社会	wfci
工作	aaat
学习	ipnu
生活	tgit
问题	ukgj
时间	jfuf
时候	whn
世界	anlw
应该	yjy
可能	cece
需要	fsv
通过	cep
进行	fotf
实现	gmpu
建设	vfyu
发展	ntna
经济	xci
技术	rsy
科学	tuip
教育	ftyc
文化	wwx
历史	dlkq
政治	ghic
政府	gwyw
企业	whog
公司	wcng
学校	ipuq
医院	aubp
银行	qtf
公司	wcng
工作	aaat
学习	ipnu
生活	tgit
问题	ukgj
时间	jfuf
时候	whn
世界	anlw
应该	yjy
可能	cece
需要	fsv
通过	cep
进行	fotf
实现	gmpu
建设	vfyu
发展	ntna
经济	xci
技术	rsy
科学	tuip
教育	ftyc
文化	wwx
历史	dlkq
政治	ghic
政府	gwyw
企业	whog
公司	wcng
学校	ipuq
医院	aubp
银行	qtf
公司	wcng

# 常用短语
大家好	vwdd
谢谢	ytyt
对不起	cfgb
没关系	rgtx
不好意思	gvnb
打扰	ssrn
麻烦	ysgg
辛苦	fyak
加油	ngim
努力	vclc
加油	ngim
恭喜	spgn
快乐	nnqi
幸福	fypy
平安	gipu
健康	wvyv
美丽	ugtm
漂亮	iyyp
聪明	bujj
智慧	dhdh
勇敢	ncbe
坚强	xkxf
努力	vclc
奋斗	ufdl
成功	dnal
失败	gmtq
希望	qdut
梦想	sshn
未来	gogo
现在	gmgu
过去	fcu
今天	wygd
明天	jegd
昨天	jtgd
早上	jh
中午	kktf
晚上	jqgd
下午	ghwf
春天	dwg
夏天	dht
秋天	tog
冬天	tug
春天	dwg
夏天	dht
秋天	tog
冬天	tug
""".trimIndent()
    }
    
    /**
     * 用户自定义配置
     */
    private fun getDefaultCustomYaml(): String {
        return """
# 用户自定义配置
patch:
  schema_list:
    - schema: wubi86
  "menu/page_size": 5
""".trimIndent()
    }
}