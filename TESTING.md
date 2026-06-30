# Test checklist — voice variations + megaphone + recipes

Needs: Fabric server + **Simple Voice Chat** + 2 clients (all with the mod jar).
Build: `JAVA_HOME=.../openjdk@21 ./gradlew :mod:build` → `mod/build/libs/blind-deaf-muted-0.1.0.jar` (drop in server + client `mods/`).

## Voice (riskiest first)
- [ ] **DEAF rebuild loop** — `/bdm set <p> deaf`, someone talks nearby. Expect faint muffled rumble. ⚠️ Listen for **doubled/echoing audio or total silence** = resend re-fires the event (re-entrancy bug).
- [ ] **MUTED** — `/bdm set <p> muted`, they talk → others hear faint crunchy garble (not silence, not clear).
- [ ] **MUTED + megaphone** — muted holds **R** (or item) → still garbled but LOUD/saturated (cuts through, still unclear).
- [ ] **Megaphone → deaf** — deaf listener + speaker holds **R** (or item) → deaf hears them loud/saturated, cutting through.
- [ ] **Codec quality** — deaf/muted audio clicky at frame edges? = filter/decoder continuity issue.

## Blind cane
- [ ] `/bdm set <p> blind` with NO cane → full blackout (default).
- [ ] Hold **cane** item → swaps to reduced "see your feet" fog. Drop it → back to blackout.
- [ ] `B` keybind still toggles mode manually (testing).
- [ ] Double-cane check: blind player holding the item shows arm-cane + held stick — looks OK or suppress cosmetic?

## Megaphone visuals
- [ ] Hold **R** → bullhorn at mouth + right arm raises (visible to others / 3rd person). Release → gone.
- [ ] Up-to-1s lag on appear/disappear acceptable.

## Recipes (crafting table)
- [ ] **Megaphone** = axe shape, iron + redstone.
- [ ] **Randomizer** = ✛ ender pearl(top) / flesh(left) / gunpowder(right) / bone(bottom) / glass bottle(center).
- [ ] Recipe missing = JSON typo → report.

## Keybinds
- [ ] Options → Controls → "Blind Deaf Muted" — all keys rebindable.

## No-SVC sanity
- [ ] Server without Simple Voice Chat still boots (voice rules just inert).

## Tuning notes (report feel)
`VoiceFx`: `DEAF_VOLUME` 0.12 · `DEAF_LOWPASS_HZ` 500 · `MUTED_VOLUME` 0.35 · `MEGAPHONE_GAIN` 6.0.
Arm/horn align: `PlayerEntityModelMixin.MEGAPHONE_ARM_PITCH/ROLL` + `MegaphoneFeatureRenderer` neck/bell cuboids.
