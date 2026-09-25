"""Writes what every vanilla food brings to the five nutrients.

Points on the 0-100 nutrient levels (see survival/Nutrient.java). The body uses about 10 points of
each per Minecraft day, so a food that brings 10 of something covers a day of it; how filling a
food is stays the vanilla hunger value. Meat is protein and fat, bread and potatoes carbohydrate and
fibre, fruit and vegetables vitamins and fibre, and a stew a little of everything - which is the point
of cooking one.

Run from the repository root:  python tools/food_nutrition.py
"""
import json
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/data/hardwrought/hardwrought/food_nutrition"

#            protein fat  carbs vitamins fibre  water
FOODS = {
    "apple":                  (0,  0,  6,  5,  3,  4),
    "baked_potato":           (1,  0, 10,  3,  3,  0),
    "potato":                 (0,  0,  6,  2,  2,  1),
    "poisonous_potato":       (0,  0,  3,  0,  1,  0),
    "beetroot":               (0,  0,  3,  4,  4,  2),
    "beetroot_soup":          (1,  0,  5,  8,  6,  8),
    "bread":                  (2,  1, 12,  1,  4,  0),
    "carrot":                 (0,  0,  3,  7,  4,  2),
    "golden_carrot":          (1,  0,  8, 14,  5,  2),
    "golden_apple":           (1,  0, 12, 12,  4,  3),
    "enchanted_golden_apple": (2,  0, 15, 20,  5,  3),
    "chorus_fruit":           (0,  0,  4,  2,  3,  2),
    "melon_slice":            (0,  0,  3,  3,  1,  6),
    "sweet_berries":          (0,  0,  3,  4,  2,  2),
    "glow_berries":           (0,  0,  3,  4,  1,  2),
    "dried_kelp":             (0,  0,  1,  4,  2,  0),
    "cookie":                 (0,  2,  5,  0,  0,  0),
    "pumpkin_pie":            (2,  5, 10,  4,  3,  0),
    "honey_bottle":           (0,  0, 14,  1,  0,  4),
    "mushroom_stew":          (3,  1,  3,  5,  5,  8),
    "rabbit_stew":            (8,  3,  6,  6,  5,  8),
    "suspicious_stew":        (1,  0,  3,  5,  5,  8),
    "beef":                   (7,  5,  0,  1,  0,  0),
    "cooked_beef":            (12, 8,  0,  1,  0,  0),
    "porkchop":               (7,  7,  0,  1,  0,  0),
    "cooked_porkchop":        (11, 10, 0,  1,  0,  0),
    "mutton":                 (6,  6,  0,  1,  0,  0),
    "cooked_mutton":          (10, 9,  0,  1,  0,  0),
    "chicken":                (6,  2,  0,  1,  0,  0),
    "cooked_chicken":         (10, 3,  0,  1,  0,  0),
    "rabbit":                 (6,  1,  0,  1,  0,  0),
    "cooked_rabbit":          (10, 2,  0,  1,  0,  0),
    "cod":                    (6,  1,  0,  2,  0,  0),
    "cooked_cod":             (9,  1,  0,  3,  0,  0),
    "salmon":                 (6,  4,  0,  3,  0,  0),
    "cooked_salmon":          (9,  6,  0,  4,  0,  0),
    "tropical_fish":          (5,  1,  0,  2,  0,  0),
    "pufferfish":             (3,  1,  0,  1,  0,  0),
    "rotten_flesh":           (3,  1,  0,  0,  0,  0),
    "spider_eye":             (2,  0,  0,  0,  0,  0),
    "milk_bucket":            (4,  4,  3,  3,  0, 10),
}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for old in OUT.glob("*.json"):
        old.unlink()
    names = ("protein", "fat", "carbohydrates", "vitamins", "fiber", "hydration")
    for item, values in FOODS.items():
        data = {"item": f"minecraft:{item}"}
        data.update({name: value for name, value in zip(names, values) if value})
        (OUT / f"{item}.json").write_text(json.dumps(data) + "\n", encoding="utf-8")
    print(f"{len(FOODS)} foods written")


if __name__ == "__main__":
    main()
