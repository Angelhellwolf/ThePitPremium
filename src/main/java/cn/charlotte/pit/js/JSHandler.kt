@file:Suppress("removal", "DEPRECATION")

package cn.charlotte.pit.js

import cn.charlotte.pit.ThePit
import cn.charlotte.pit.data.PlayerProfile
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity
import cn.charlotte.pit.enchantment.type.JSEnchantment
import cn.charlotte.pit.enchantmentInt
import cn.charlotte.pit.util.chat.CC
import jdk.dynalink.beans.StaticClass
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.openjdk.nashorn.api.scripting.ScriptObjectMirror
import java.io.File
import java.util.*
import java.util.function.Consumer
import java.util.function.Function
import javax.script.*
import kotlin.math.pow

object JSHandler {

    private const val EXPORT_SUFFIX = """
; (function() {
  var __g;
  try {
    __g = (typeof globalThis !== 'undefined') ? globalThis : this;
  } catch (e) {
    __g = this;
  }
  function __val(name) {
    try {
      var v = eval(name);
      return (typeof v === 'undefined') ? null : v;
    } catch (e) {
      return null;
    }
  }
  function __copy(name) {
    try {
      var v = __val(name);
      if (v !== null) { __g[name] = v; }
      return v;
    } catch (e) {
      return null;
    }
  }

  __g.__pit_export = {
    ENCHANT_NAME: __copy('ENCHANT_NAME'),
    INTERNAL_NAME: __copy('INTERNAL_NAME'),
    RARITY: __copy('RARITY'),
    DESCRIPTIONS: __copy('DESCRIPTIONS'),
    TYPE: __copy('TYPE'),
    MAX_LEVEL: __copy('MAX_LEVEL'),
    lore: __copy('lore'),

    attack: __copy('attack'),
    killed: __copy('killed'),
    shoot: __copy('shoot'),
    assist: __copy('assist'),
    be_killed: __copy('be_killed'),
    be_damaged: __copy('be_damaged')
  };
})();
"""

    private val defaultBundledScripts = listOf(
        // Keep this list in sync with src/main/resources/js/*
        "example.js",
        "hsr_abundance_regen.js",
        "hsr_abundance_salvage.js",
        "hsr_aiming_protocol.js",
        "hsr_destruction_resonance.js",
        "hsr_destruction_riposte.js",
        "hsr_erudition_spread.js",
        "hsr_erudition_stance.js",
        "hsr_followup_arrow.js",
        "hsr_harmony_reprise.js",
        "hsr_harmony_track.js",
        "hsr_hunt_afterimage.js",
        "hsr_hunt_chain.js",
        "hsr_nihility_lag.js",
        "hsr_preservation_counter.js",
        "hsr_quantum_entangle.js",
        "hsr_rift_execution.js",
        "hsr_shield_pulse.js",
        "hsr_starrail_sniper.js",
        "hsr_stellar_brand.js",
        "hsr_toughness_slash.js",
    )

    private val scriptEngineFactory by lazy {
        val classLoader = JSHandler::class.java.classLoader
        val candidates = linkedSetOf(
            // Resolve factory class from the runtime package of ScriptObjectMirror.
            // This keeps discovery working both before and after Shadow relocation.
            ScriptObjectMirror::class.java.name.replace(
                "ScriptObjectMirror",
                "NashornScriptEngineFactory"
            ),
            // Java 15+ (and bundled dependency): org.openjdk.nashorn:nashorn-core
            "org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory",
            // Java 8–14 (JDK built-in module)
            "jdk.nashorn.api.scripting.NashornScriptEngineFactory",
        )

        for (className in candidates) {
            try {
                val cls = Class.forName(className, true, classLoader)
                val instance = cls.getDeclaredConstructor().newInstance()
                return@lazy instance as ScriptEngineFactory
            } catch (_: Throwable) {
                // ignore and try next
            }
        }

        throw IllegalStateException(
            "Nashorn ScriptEngineFactory not found. " +
                "On Java 15+ you must bundle org.openjdk.nashorn:nashorn-core (or run Java 8–14)."
        )
    }

    private val arraysClass: StaticClass = StaticClass.forClass(Arrays::class.java)
    private val bukkitClass: StaticClass = StaticClass.forClass(Bukkit::class.java)
    private val playerClass: StaticClass = StaticClass.forClass(Player::class.java)
    private val runnableClass: StaticClass = StaticClass.forClass(Runnable::class.java)

    private val loadedJsEnchant = HashMap<String, JSEnchantment>()

    private var version = 0

    fun load() {
        val jsDir = File(ThePit.getInstance().dataFolder, "js")
        if (!jsDir.exists()) {
            jsDir.mkdirs()
        }

        ensureBundledScripts(jsDir)

        jsDir.walk().forEach {
            if (it.extension == "js") {
                val script = it.readText()
                if (load(script, it)) {
                    println("已加载JS附魔 ${it.name}")
                }
            }
        }
    }

    private fun ensureBundledScripts(jsDir: File) {
        for (name in defaultBundledScripts) {
            val target = File(jsDir, name)
            if (target.exists()) continue

            val resource = ThePit.getInstance().getResource("js/$name") ?: continue
            target.parentFile?.mkdirs()
            target.outputStream().use { out ->
                resource.use { input -> input.copyTo(out) }
            }
        }
    }

    private fun load(script: String, file: File): Boolean {
        val existJsEnchant = loadedJsEnchant.remove(file.name)
        if (existJsEnchant != null) {
            ThePit.getInstance().enchantmentFactor.apply {
                enchantments -= existJsEnchant
                enchantmentMap.remove(existJsEnchant.nbtName)
                playerDamageds -= existJsEnchant
                attackEntities -= existJsEnchant
                playerBeKilledByEntities -= existJsEnchant
                playerShootEntities -= existJsEnchant
                playerAssists -= existJsEnchant
            }
            enchantmentInt -= 1
        }
        try {
            val engine = createScriptEngineEnv(script)

            val invocable = engine as Invocable

            val attackFunc = (getVar(engine, "attack") as? ScriptObjectMirror)?.isFunction == true
            val killedFunc = (getVar(engine, "killed") as? ScriptObjectMirror)?.isFunction == true
            val shootFunc = (getVar(engine, "shoot") as? ScriptObjectMirror)?.isFunction == true
            val assistFunc = (getVar(engine, "assist") as? ScriptObjectMirror)?.isFunction == true
            val beKilledFunc = (getVar(engine, "be_killed") as? ScriptObjectMirror)?.isFunction == true
            val beDamagedFunc = (getVar(engine, "be_damaged") as? ScriptObjectMirror)?.isFunction == true

            Bukkit.getScheduler().runTaskLater(ThePit.getInstance(), Runnable {

            }, 1L)

            val enchantName = requireNotNull(getVar(engine, "ENCHANT_NAME")) {
                "ENCHANT_NAME not found in ${file.name}"
            }.toString()
            val internalName = requireNotNull(getVar(engine, "INTERNAL_NAME")) {
                "INTERNAL_NAME not found in ${file.name}"
            }.toString()
            val rarity = requireNotNull(getVar(engine, "RARITY")) {
                "RARITY not found in ${file.name}"
            }.toString()
            val maxLevel = (getVar(engine, "MAX_LEVEL") as? Number)?.toInt()?.coerceAtLeast(1) ?: 3

            val jsEnchantment = JSEnchantment(
                engine, enchantName,
                internalName,
                EnchantmentRarity.valueOf(rarity.uppercase()),
                maxLevel,
                resolveDescriptionProvider(engine, file),
                readStringSet(getVar(engine, "TYPE"), "TYPE", file),
                { enchantLevel, attacker, victim, damage, finalDamage, boostDamage, cancel ->
                    val safeLevel = enchantLevel.coerceIn(1, maxLevel)
                    if (attackFunc) {
                        invocable.invokeFunction(
                            "attack",
                            safeLevel,
                            attacker,
                            victim,
                            damage,
                            finalDamage,
                            boostDamage,
                            cancel
                        )
                    }
                },
                { enchantLevel, myself, target, coins, experience ->
                    val safeLevel = enchantLevel.coerceIn(1, maxLevel)
                    if (killedFunc) {
                        invocable.invokeFunction("killed", safeLevel, myself, target, coins, experience)
                    }
                },
                { enchantLevel, attacker, target, damage, finalDamage, boostDamage, cancel ->
                    val safeLevel = enchantLevel.coerceIn(1, maxLevel)
                    if (shootFunc) {
                        invocable.invokeFunction(
                            "shoot",
                            safeLevel,
                            attacker,
                            target,
                            damage,
                            finalDamage,
                            boostDamage,
                            cancel
                        )
                    }
                },
                { enchantLevel, myself, target, damage, finalDamage, coins, experience ->
                    val safeLevel = enchantLevel.coerceIn(1, maxLevel)
                    if (assistFunc) {
                        invocable.invokeFunction(
                            "assist",
                            safeLevel,
                            myself,
                            target,
                            damage,
                            finalDamage,
                            coins,
                            experience
                        )
                    }
                },
                { enchantLevel, myself, target, coins, experience ->
                    val safeLevel = enchantLevel.coerceIn(1, maxLevel)
                    if (beKilledFunc) {
                        invocable.invokeFunction("be_killed", safeLevel, myself, target, coins, experience)
                    }
                },
                { enchantLevel, myself, attacker, damage, finalDamage, boostDamage, cancel ->
                    val safeLevel = enchantLevel.coerceIn(1, maxLevel)
                    if (beDamagedFunc) {
                        invocable.invokeFunction(
                            "be_damaged",
                            safeLevel,
                            myself,
                            attacker,
                            damage,
                            finalDamage,
                            boostDamage,
                            cancel
                        )
                    }
                },
            )

            loadedJsEnchant[file.name] = jsEnchantment
            ThePit.getInstance().enchantmentFactor.apply {
                enchantments += jsEnchantment
                enchantmentMap[jsEnchantment.internalName0] = jsEnchantment
                if (beDamagedFunc) {
                    playerDamageds += jsEnchantment
                }
                if (attackFunc) {
                    attackEntities += jsEnchantment
                }
                if (beKilledFunc) {
                    playerBeKilledByEntities += jsEnchantment
                }
                if (shootFunc) {
                    playerShootEntities += jsEnchantment
                }
                if (assistFunc) {
                    playerAssists += jsEnchantment
                }

                enchantmentInt += 1
            }
            return true
        } catch (e: Exception) {
            println("加载 ${file.name} 时出现错误")
            e.printStackTrace()
            return false
        }
    }

    private fun resolveDescriptionProvider(engine: ScriptEngine, file: File): (Int) -> List<String> {
        val explicit = getVar(engine, "DESCRIPTIONS")
        val explicitList = tryReadStringList(explicit)

        // Backward/alternative schema: provide a lore(level) function instead of a DESCRIPTIONS constant.
        val loreFunc = getVar(engine, "lore") as? ScriptObjectMirror
        if (loreFunc != null && loreFunc.isFunction) {
            return { level ->
                val safeLevel = level.coerceAtLeast(1)
                try {
                    val result = loreFunc.call(null, safeLevel)
                    tryReadStringList(result)
                        ?: explicitList
                        ?: throw IllegalStateException(
                            "lore(level) must return an array/list of strings in ${file.name} " +
                                "(got ${result?.javaClass?.name ?: "null"})"
                        )
                } catch (_: Exception) {
                    explicitList ?: throw IllegalStateException("lore(level) execution failed in ${file.name}")
                }
            }
        }

        if (explicitList != null) {
            return { _ -> explicitList }
        }

        throw IllegalStateException(
            "DESCRIPTIONS missing/invalid in ${file.name} and no usable lore(level) function found " +
                "(DESCRIPTIONS=${explicit?.javaClass?.name ?: "null"})"
        )
    }

    private fun createScriptEngineEnv(script: String): ScriptEngine {
        val engine = scriptEngineFactory.scriptEngine

        // Do NOT replace ENGINE_SCOPE bindings with a new map.
        // Replacing can make script-defined globals (e.g. ENCHANT_NAME/DESCRIPTIONS) invisible via engine.get(...).
        val bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE)

        // 将 Lambda 包装为 Nashorn 可识别的函数
        bindings["print"] = Consumer { args: Any? ->
            println(args)
        }

        // 定义 pitProfile 为 Function<Player, PlayerProfile>
        bindings["pit_profile"] = Function<Player, PlayerProfile> { player ->
            PlayerProfile.getPlayerProfileByUuid(player.uniqueId)
        }

        bindings["translate"] = Function<String, String> { input ->
            CC.translate(input)
        }

        // 其他绑定
        bindings["Pit"] = ThePit.getInstance()
        bindings["Bukkit"] = bukkitClass
        bindings["Arrays"] = arraysClass
        bindings["Player"] = playerClass
        bindings["Runnable"] = runnableClass

        // Evaluate the script AND export variables/functions in the same compilation unit.
        // This makes top-level let/const visible even on newer Nashorn implementations.
        engine.eval(script + "\n" + EXPORT_SUFFIX)
        return engine
    }

    private fun isJsUndefined(value: Any): Boolean {
        val name = value.javaClass.name
        return name.endsWith(".Undefined") || name.contains("nashorn.internal.runtime.Undefined")
    }

    private fun getVar(engine: ScriptEngine, key: String): Any? {
        val exported = engine.get("__pit_export") as? ScriptObjectMirror
        if (exported != null && exported.hasMember(key)) {
            val member = exported.getMember(key)
            if (member != null && !isJsUndefined(member)) {
                return member
            }
        }

        engine.get(key)?.let { return it }

        val bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE)
        if (bindings.containsKey(key)) return bindings[key]

        return try {
            engine.eval(key)
        } catch (_: Exception) {
            null
        }
    }

    private fun tryReadStringList(value: Any?): List<String>? {
        return when (value) {
            null -> null
            is ScriptObjectMirror -> value.mapNotNull { it.value as? String }.toList()
            is Iterable<*> -> value.mapNotNull { it as? String }.toList()
            is Array<*> -> value.mapNotNull { it as? String }.toList()
            else -> null
        }
    }

    private fun readStringList(value: Any?, key: String, file: File): List<String> {
        val list = tryReadStringList(value)

        if (list == null) {
            throw IllegalStateException("$key must be an array/list of strings in ${file.name} (got ${value?.javaClass?.name ?: "null"})")
        }
        return list
    }

    private fun readStringSet(value: Any?, key: String, file: File): Set<String> {
        return readStringList(value, key, file).toSet()
    }

}
