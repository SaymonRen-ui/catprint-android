package com.catprint.ui.screens

/**
 * Набор эмодзи как в десктопном EmojiData.
 * Для термопечати (1 бит) чётче всего идут «Символы».
 */
data class EmojiCategory(val title: String, val items: List<String>)

object EmojiData {
    val categories: List<EmojiCategory> = listOf(
        EmojiCategory(
            "😀 Лица",
            "😀 😁 😂 🤣 😊 😍 😎 🤔 😴 😭 😡 🥳 😇 🙃 😉 🥺 😜 😝 🤪 😋 🤗 🤭 🤫 😶 😐 🙄 😬 😮 😲 😳 🥵 🥶 😱 😨 😰 😥 🤤 😪 😵 🤠 😷 🤒 🤕 🤢 🤮 🥴 🤯 🥱 😺 😸 😹 😻 🙀 😿 😼 🙈 🙉 🙊"
                .split(' ').filter { it.isNotEmpty() }
        ),
        EmojiCategory(
            "👍 Жесты",
            "👍 👎 👏 🙏 ✌ 🤝 👋 💪 ☝ 👀 🫶 🤟 👌 ✊ 👐 🤲 ✋ 🤚 🖐 👊 🤛 🤜 👈 👉 👆 👇 ✍ 💅 🤳 💃 🕺 🚶 🏃 👯 🙇 💁 🙅 🙆 🤦 🤷 🧍 🧎 🙋 🤸 ⛹ 🧘"
                .split(' ').filter { it.isNotEmpty() }
        ),
        EmojiCategory(
            "❤ Сердца",
            "❤ 🧡 💛 💚 💙 💜 🖤 🤍 💔 ❣ 💕 💞 💓 💗 💖 💘 💝 💟 💋 🌹 🥀 💐 🌺 🌷 🌸 💮 🏵"
                .split(' ').filter { it.isNotEmpty() }
        ),
        EmojiCategory(
            "🐶 Животные",
            "🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🐒 🐔 🐧 🐦 🐤 🦆 🦅 🦉 🦇 🐺 🐗 🐴 🦄 🐝 🐛 🦋 🐌 🐞 🐜 🐢 🐍 🦎 🐙 🦑 🦐 🦞 🦀 🐡 🐠 🐟 🐬 🐳 🐋 🦈 🐊 🐅 🐆 🦓 🐘 🦛 🦏 🐪 🦒 🦘 🐎 🐖 🐏 🐑 🐐 🦌 🐕 🐩 🐈 🐓 🦃 🦚 🦜 🦢 🕊 🐇 🦝 🦨 🦡 🦦 🐁 🐀 🐿 🦔 🐾 🦂 🦟 🐚 🪲 🪳 🦗 🪰 🪱 🦠"
                .split(' ').filter { it.isNotEmpty() }
        ),
        EmojiCategory(
            "🍕 Еда",
            "🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🫐 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🥑 🍆 🥔 🥕 🌽 🌶 🥒 🥬 🥦 🧄 🍄 🥜 🌰 🍞 🥐 🥖 🥨 🥯 🥞 🧇 🧀 🍖 🍗 🥩 🍔 🍟 🍕 🌭 🥪 🌮 🌯 🥙 🥚 🍳 🍿 🍱 🍙 🍚 🍛 🍜 🍝 🍣 🍤 🍡 🥟 🍦 🍧 🍨 🍩 🍪 🎂 🍰 🧁 🍫 🍬 🍭 🍯 🍼 🥛 ☕ 🍵 🥤 🧋 🍺 🍻 🥂 🍷 🍸 🍹 🍾 🫖"
                .split(' ').filter { it.isNotEmpty() }
        ),
        EmojiCategory(
            "⚽ Активности",
            "⚽ 🏀 🏈 ⚾ 🎾 🏐 🏉 🎱 🏓 🏸 🏒 🏑 🥍 🏏 ⛳ 🏹 🎣 🥊 🥋 🛹 🛼 ⛸ 🎿 ⛷ 🏂 🏋 🤼 🤺 🏌 🏇 🏄 🏊 🚣 🧗 🚴 🤹 🎪 🎭 🎨 🎬 🎤 🎧 🎹 🥁 🎷 🎺 🎸 🎻 🎲 ♟ 🎯 🎳 🎮 🧩 🎰 🪕 🪗"
                .split(' ').filter { it.isNotEmpty() }
        ),
        EmojiCategory(
            "✈ Путешествия",
            "🚗 🚕 🚙 🚌 🏎 🚓 🚑 🚒 🚐 🛻 🚚 🚜 🛴 🚲 🛵 🏍 🚨 🚍 🚘 🚖 🚡 🚠 🚃 🚋 🚞 🚝 🚄 🚂 🚇 ✈ 🛫 🛬 🛰 🚀 🛸 🚁 🛶 ⛵ 🚤 🛥 🛳 ⛴ 🚢 ⚓ ⛽ 🚧 🚦 🚏 🗺 🗽 🗼 🏰 🏯 🏟 🎡 🎢 🎠 ⛲ 🏖 🏝 🌋 ⛰ 🏔 🗻 🏕 ⛺ 🏠 🏡 🏢 🏥 🏦 🏨 🏪 🏫 🏭 💒 🌉 🌃 🌆 🌇 🎆 🎇"
                .split(' ').filter { it.isNotEmpty() }
        ),
        EmojiCategory(
            "💡 Объекты",
            "💡 🔦 🕯 🧯 💸 💵 💰 💳 💎 ⚖ 🧰 🔧 🔨 ⚙ ⛓ 🧲 🔫 💣 🧨 🪓 🔪 🗡 ⚔ 🛡 📿 🔮 🧿 💈 🔭 🔬 💊 💉 🩹 🌡 🏷 🔖 🚩 🎫 🎟 🎖 🏆 🏅 🥇 🥈 🥉 💌 📦 📯 📻 📱 📲 ☎ 📞 📟 📠 🔋 🪫 💻 🖥 🖨 ⌨ 🖱 🖲 💽 💾 💿 📀 🧮 🎥 📽 📺 📷 📸 📹 📼 🔍 🕰 ⏰ ⏱ ⏲ ⌛ ⏳ ⌚ 🧭 🗝 🔑 🪄 🎈 🎀 🎁 🎗 🏮 🪔 ✉ 📩 📨 📧 📥 📤 📫 📪 📮 🖊 🖋 ✒ 🖌 🖍 📝 ✏ 📏 📐 ✂ 📌 📍 🗑 🔒 🔓 🔏 🔐 🪚 🧷 🖇 📎"
                .split(' ').filter { it.isNotEmpty() }
        ),
        EmojiCategory(
            "★ Символы — чётче всего",
            "★ ☆ ♥ ♦ ♣ ♠ ● ○ ▲ △ ■ □ ⬛ ⬜ ✔ ✖ → ← ↑ ↓ ➕ ➖ ✨ ❤ ☎ ⚙ ✂ ⛔ ☑ ☒ ◀ ▶ № © ® ™ ℹ ♻ ⚠ ☢ ☣ ⬆ ⬇ ➡ ⬅ ↔ ↕ 🔀 🔁 🔂 ▶ ⏸ ⏯ ⏹ ⏭ ⏮ 🔼 🔽 ⏫ ⏬ ▪ ▫ ◾ ◽ ◼ ◻ 🔴 🟠 🟡 🟢 🔵 🟣 ⚫ ⚪ 🟤 🔶 🔷 🔸 🔹 🔺 🔻 💠 ❇ ✳ ❎ ✅ ✔ ❌ ➰ ➿ ‼ ⁉ ❓ ❔ ❗ ❕ 💯 🔠 🔡 🔢 🆘 🅰 🅱 🆎 🆑 📛 🔞 📵 🚯 🔕 🔇 📣 📢 🔊 🆙 🆒 🆕 🆓 🔟 ☯ ☮ ✝ ☪ 🕉 ☸ ✡ ☦ 🛐 ⛎ ♈ ♉ ♊ ♋ ♌ ♍ ♎ ♏ ♐ ♑ ♒ ♓ ⏏ ♾️"
                .split(' ').filter { it.isNotEmpty() }
        )
    )
}
