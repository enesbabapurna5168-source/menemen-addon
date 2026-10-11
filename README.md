# Menemen Addon

Meteor Client icin Minecraft 1.21.11 addon'u. Base bulmaya yonelik ESP modulu icerir.

## Modul: Base Finder ESP

- Yuklu chunklarda oyuncu yapimi bloklari (sandik, barrel, shulker, firin, hopper, yatak vb.) vurgular.
- Y araligi ayarlanabilir, varsayilan **-64 ile 320** (en dipten itibaren hepsini gosterir).
- Blok listesi, renkler, cizim sekli, tracer, chunk yaricapi ve tarama hizi ayarlanabilir.
- Bir chunkta cok sayida depolama blogu bulununca sohbette uyari verir.

Modul tamamen istemci tarafinda calisir: sunucuya paket gondermez, sadece yuklenmis chunk
verisini okuyup cizer. Anti-cheat atlatma kodu icermez.

## Derleme

Gereksinimler: JDK 21, internet (maven.fabricmc.net, maven.meteordev.org, libraries.minecraft.net).

```
./gradlew build        # Windows: gradlew.bat build
```

Cikti: `build/libs/menemen-addon-0.1.0.jar`. Bu dosyayi `.minecraft/mods` klasorune,
Meteor Client ve Fabric Loader ile birlikte koyun.

Gradle wrapper yoksa: `gradle wrapper --gradle-version 8.14` calistirin.

## Not

`gradle/libs.versions.toml` icindeki surumler (Yarn, Loom, Fabric Loader, Meteor snapshot)
1.21.11 icin bellekten yazildi ve dogrulanamadi. Derleme cozumleme hatasi verirse
guncel degerleri https://fabricmc.net/develop/ ve https://maven.meteordev.org/ adresinden alin.
