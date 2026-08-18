# 006 — Category taxonomy

## Context

The app shipped 12 grocery-centric categories. "Babe, Get This" is a general shopping
app, not a grocery app, so items like clothing, oven mitts, and cooked dishes (tom yum,
rice bowls) had no obvious home. We also want automatic categorization — a keyword map
for typed items and an LLM for voice input — which only works if category boundaries are
unambiguous: the same item must always land in the same category.

## Decision

Ship a flat set of **48 categories**, designed so every plausible shopping item has
exactly one home. No hierarchy, no cuisine/origin categories (a dish resolves by
ingredient/aisle, not by cuisine), one `Other` catch-all of last resort.

The set was drafted from three lenses (store-aisle, real shopping lists, e-commerce
taxonomy), merged, then stress-tested against ~420 real and adversarial items over two
rounds. Overlaps were killed by merging categories and by writing explicit tiebreak
rules, not by adding more categories (more categories create more overlap).

The 12 original category IDs are preserved for sync/back-compat. Two were renamed in
place:

- `cat-pantry-dry-goods`: "Pantry & Dry Goods" → **"Rice, Grains & Pasta"**
- `cat-toiletries-personal-care`: "Toiletries & Personal Care" → **"Personal Care & Beauty"**

The list itself lives in `DefaultCategories.kt`. This doc holds the **boundary rules** —
the categorizer (LLM prompt and reviewer) uses them; the app does not need them at
runtime.

## Global tiebreak rules

These override individual category rules when they conflict:

1. **Product identity beats intended use.** Baking soda is always Baking, even when
   bought to clean. Cleaning vinegar is always Oils, Vinegars & Cooking Fats, not
   Household & Cleaning.
2. **Frozen always wins.** Frozen meat, frozen dough, frozen ready meals → Frozen Foods,
   over every other food category.
3. **Pet-specific always wins.** Anything for an animal → Pet Supplies, including
   pet car gear (dog seat cover) over Automotive, and wild bird seed.
4. **Baby/toddler-labeled food beats Snacks.** Toddler puffs → Baby & Kids. But kids'
   versions of adult products (children's medicine, kids' clothing/shoes/toothpaste) go
   to the adult product's category, not Baby & Kids.
5. **Label-based splits** where one word covers two products:
   - "cooking wine" / mirin → Sauces, Condiments & Pastes; all drinkable wine (incl.
     marsala) → Beer, Wine & Spirits.
   - Cessation-labeled nicotine (gum, patches) → Health & Pharmacy; recreational
     nicotine (cigarettes, vapes, pouches) → Tobacco & Vaping.

## Per-category boundary rules

### Food

- **Fruits & Vegetables** — Fresh produce and fresh herbs only. NOT frozen (→Frozen
  Foods), canned (→Canned & Jarred Goods), dried fruit/nuts (→Snacks), dried
  mushrooms/seaweed sheets (→Baking & Dry Ingredients), cut flowers (→Garden & Outdoor).
- **Dairy & Eggs** — Chilled dairy, eggs, block/tub butter, plant-based milk/yogurt/
  cheese. Shelf-stable cooking fats (ghee, lard) →Oils, Vinegars & Cooking Fats; ice
  cream →Frozen Foods; tofu →Meat & Seafood.
- **Meat & Seafood** — Fresh, cured, or deli meat/poultry/fish, plus tofu and
  plant-based meat. Frozen →Frozen Foods; canned fish →Canned & Jarred Goods; cooked
  complete meals (rotisserie) →Ready Meals & Prepared Food.
- **Bakery & Bread** — Fresh / in-store-bakery bread, buns, tortillas, pastries, cakes.
  Shelf-stable packaged snack cakes and cookies →Sweets & Chocolate; raw/refrigerated
  dough →Baking & Dry Ingredients.
- **Frozen Foods** — Anything sold frozen. Frozen always wins (see global rule 2).
- **Rice, Grains & Pasta** (`cat-pantry-dry-goods`) — Rice, pasta, noodles in any
  non-frozen form (dry, fresh, instant, microwave pouches), plus grains, dried legumes,
  Korean tteok. Popcorn kernels and puffed rice cakes →Snacks; rice paper wrappers
  →Baking & Dry Ingredients; cereal/oats →Breakfast & Cereal; rice bowls the dishware
  →Kitchen & Dining.
- **Canned & Jarred Goods** — Preserved whole/chopped ingredients in cans, jars,
  cartons — beans, tomatoes, fish, canned/carton soup, broth, pickles, coconut milk. ALL
  cooking pastes incl. tomato paste →Sauces, Condiments & Pastes; instant soup sachets
  →Spices & Seasonings; jams →Spreads, Honey & Jam.
- **Baking & Dry Ingredients** (`cat-baking-supplies`) — Flour, sugar, baker's yeast,
  baking soda (even for cleaning), extracts, baking chocolate, cake mixes, dough/pastry,
  breadcrumbs, cornstarch, dried mushroom/seaweed ingredient sheets (nori), rice
  paper/wonton wrappers. Nutritional yeast →Spices; seasoned seaweed snack packs
  →Snacks; finished baked goods →Bakery & Bread.
- **Spices & Seasonings** — Dried herbs, ground spices, salt, stock cubes, nutritional
  yeast, and ALL instant soup/gravy/seasoning sachets whether dry or paste inside (French
  onion soup mix, instant miso). Fresh herbs →Fruits & Vegetables; bottled/jarred wet
  sauces →Sauces, Condiments & Pastes.
- **Sauces, Condiments & Pastes** — All wet sauces, dressings, dips, table condiments,
  and ALL savory cooking pastes in any container — tomato paste, tom yum paste, curry
  paste, tahini and every sesame paste — plus "cooking wine" and mirin. Drinkable wine
  →Beer, Wine & Spirits; oils →Oils, Vinegars & Cooking Fats; sweet spreads →Spreads,
  Honey & Jam.
- **Oils, Vinegars & Cooking Fats** — ALL cooking oils (plain or infused, truffle oil),
  cooking spray, ALL vinegars incl. large-format cleaning vinegar, and shelf-stable
  cooking fats (ghee, lard, coconut oil). Dressings/sauces →Sauces, Condiments & Pastes.
- **Breakfast & Cereal** — Cereal, oats, granola, muesli, pancake mix, toaster pastries
  (Pop-Tarts). Granola/snack bars →Snacks; syrup, jam, nut butters →Spreads, Honey & Jam.
- **Spreads, Honey & Jam** — Sweet spreads, syrups, nut butters eaten on bread — jam,
  honey, peanut/almond butter, chocolate spread, maple syrup. ALL sesame/savory pastes
  →Sauces; block butter →Dairy & Eggs.
- **Snacks** — Savory snacks, nuts, dried fruit, popcorn in every form, puffed rice
  cakes, seasoned seaweed snack packs, ALL bar-shaped snacks. RTD protein shakes,
  powders, gels →Health & Pharmacy; baby/toddler snacks →Baby & Kids; chocolate, candy,
  cookies →Sweets & Chocolate.
- **Sweets & Chocolate** — Chocolate, candy, gum, mints, packaged cookies/biscuits,
  shelf-stable packaged snack cakes. Nicotine gum →Health & Pharmacy; ice cream →Frozen
  Foods; fresh bakery cakes →Bakery & Bread; baking chocolate →Baking & Dry Ingredients.
- **Ready Meals & Prepared Food** — Complete ready-to-eat / heat-and-eat prepared DISHES
  (chilled, shelf-stable, or hot-counter). Plain sides/components (microwave rice pouch,
  fresh pasta) go by ingredient →Rice, Grains & Pasta; frozen meals →Frozen Foods.
- **Beverages** — Ready-to-drink non-alcoholic drinks — water, soda, juice, energy
  drinks, bottled iced coffee/tea, kombucha, squash/cordial. RTD protein shakes →Health &
  Pharmacy; brew-at-home concentrates →Coffee & Tea; alcohol →Beer, Wine & Spirits; milk
  →Dairy & Eggs.
- **Coffee & Tea** — Coffee, tea, matcha, cocoa in any brew-at-home form incl. pods and
  cold brew concentrate, plus non-mains brew gear (French press, moka pot, drippers,
  filters, milk frothers of any power). Coffee machines and kettles →Appliances; bottled
  RTD →Beverages.
- **Beer, Wine & Spirits** — All drinkable alcohol incl. marsala and fortified wines,
  plus non-alcoholic beer/wine shelved with it. "Cooking wine"/mirin →Sauces; soft
  drinks →Beverages.

### Home & household

- **Household & Cleaning** — Cleaning/laundry chemicals, tools, gear — detergent, rubber
  AND disposable gloves, laundry baskets, drying racks, ironing boards, trash cans/bags,
  home pest-control, matches/lighters. Dish drying racks →Kitchen & Dining; air
  fresheners, diffusers, candles →Home Decor; car air fresheners →Automotive; paper goods
  →Paper & Disposables.
- **Paper & Disposables** — Household/kitchen single-use paper and plastic — toilet
  paper, tissues, foil, cling film, zip bags, disposable plates/cutlery/napkins.
  Personal-hygiene disposables (cotton swabs) →Personal Care & Beauty; disposable gloves
  and trash bags →Household & Cleaning; reusable containers →Kitchen & Dining.
- **Kitchen & Dining** — Non-electric and battery-powered kitchenware — cookware,
  utensils, knives, dishware (rice bowls), drinkware, oven mitts, dish towels, dish drying
  racks, food storage, insulated lunch bags/boxes, reusable bottles, digital scales and
  timers. Large cooler bags/boxes →Sports & Outdoors; mains-powered machines →Appliances;
  disposables →Paper & Disposables.
- **Appliances** — Mains-powered household machines — rice cookers, air fryers, kettles,
  coffee machines, vacuums, fans, irons, sewing machines. Electric blankets →Bedding &
  Bath; body-grooming electricals →Personal Care & Beauty; milk frothers →Coffee & Tea;
  personal tech →Electronics.
- **Furniture & Storage** — Indoor furniture, regular bed mattresses, shelving, storage
  bins, hangers. Air mattresses →Sports & Outdoors; crib mattresses →Baby & Kids; laundry
  baskets →Household & Cleaning; patio furniture →Garden & Outdoor; sheets/pillows
  →Bedding & Bath.
- **Home Decor** — Decorative items and ALL home fragrance — frames (non-digital),
  vases, rugs, curtains, cushions, wall art, plain string lights, plus ALL candles incl.
  scented/citronella, reed diffusers, room air-freshener sprays, wax melts. Birthday/
  holiday candles/decor →Party, Gifts & Holiday; ALL lamps incl. salt lamps →Lighting &
  Electrical; digital photo frames →Electronics; plant pots →Garden & Outdoor.
- **Bedding & Bath** — Bed and bath textiles — sheets, duvets, bed pillows, blankets
  incl. electric blankets, ALL towels incl. beach towels, bath mats, shower curtains.
  Bathrobes →Underwear, Socks & Sleepwear; travel neck pillows →Bags & Luggage;
  mattresses →Furniture & Storage; dish towels →Kitchen & Dining.
- **Lighting & Electrical** — ALL lamps and light bulbs (smart, decorative, salt lamps)
  and household electrical infrastructure — batteries, battery chargers, extension cords,
  power strips, wall/smart plugs, plug-in timers. Decorative string lights →Home Decor;
  holiday lights →Party, Gifts & Holiday; device chargers/cables →Electronics.
- **Hardware & Tools** — Hand/power tools, fasteners, strong adhesives, wall AND spray
  paint, safety gear incl. smoke/CO detectors, lubricants (WD-40), home-maintenance
  supplies — furnace/HVAC filters, ice melt, weather stripping, replacement parts.
  Artist/craft paint →Arts, Crafts & Hobbies; bulbs/batteries/cords →Lighting &
  Electrical; garden tools →Garden & Outdoor.
- **Garden & Outdoor** — Plants, fresh-cut flowers, seeds, soil, plant pots (indoor or
  outdoor), garden tools, hoses, patio furniture, beach umbrellas/parasols, grills,
  charcoal, lighter fluid. Camping/sports gear →Sports & Outdoors; citronella candles
  →Home Decor; matches/lighters →Household & Cleaning.
- **Automotive** — Anything car- or motorbike-specific — fluids, wipers, car care,
  mounts, car air fresheners, ice scrapers. Pet-specific car gear (dog seat covers, pet
  barriers) →Pet Supplies; general-purpose tools →Hardware & Tools.

### Personal & health

- **Personal Care & Beauty** (`cat-toiletries-personal-care`) — All body care and beauty
  for any age — hygiene, hair care incl. medicated/dandruff shampoo, oral, skin, makeup,
  fragrance, sunscreen, sanitizer, period care, cotton swabs/pads/balls, bath additives
  (epsom salt, bubble bath), grooming tools, body-grooming electricals (hair dryer,
  electric razor/toothbrush). Medicines, supplements, eye/lens care liquids →Health &
  Pharmacy; hair scrunchies/clips →Accessories & Jewelry.
- **Health & Pharmacy** — OTC medicines for any age, vitamins, supplements, ALL sports
  nutrition (protein powder, RTD protein shakes, energy gels), first aid,
  cessation-labeled nicotine, contraception, eye/lens care (contact lens solution, eye
  drops), health devices — thermometers, heating pads, reading glasses. Other
  non-prescription eyewear →Accessories & Jewelry; medicated shampoo →Personal Care &
  Beauty; recreational nicotine →Tobacco & Vaping.

### Wearables

- **Clothing** — Outer garments for all ages incl. leggings, activewear, regular
  swimwear, baby clothes. Wetsuits →Sports & Outdoors; ALL bras/socks/tights/sleepwear
  →Underwear, Socks & Sleepwear; footwear →Shoes & Footwear; hats/belts/scarves
  →Accessories & Jewelry.
- **Underwear, Socks & Sleepwear** — Underlayers and nightwear for all ages — underwear,
  ALL bras incl. sports bras, ALL socks incl. compression, sheer tights/hosiery, pajamas,
  robes/bathrobes. Leggings and other outer garments →Clothing.
- **Shoes & Footwear** — All footwear for all ages plus laces, insoles, shoe care. Socks
  →Underwear, Socks & Sleepwear.
- **Accessories & Jewelry** — Worn fashion accessories — jewelry, non-smart watches,
  belts, hats, scarves, fashion gloves, hair accessories, personal rain umbrellas, ALL
  non-prescription eyewear except reading glasses (sunglasses, blue-light glasses).
  Reading glasses →Health & Pharmacy; smartwatches →Electronics; sport gloves →Sports &
  Outdoors; bags →Bags & Luggage.
- **Bags & Luggage** — ALL carried/worn bags — handbags, backpacks, wallets, fanny
  packs, suitcases — plus travel accessories: packing cubes, travel adapters, travel neck
  pillows. Insulated lunch bags →Kitchen & Dining; cooler bags →Sports & Outdoors;
  belts/watches →Accessories & Jewelry.

### People & pets

- **Baby & Kids** — Items with no adult equivalent, plus ANYTHING labeled for babies/
  toddlers as food/feeding — diapers, wipes, formula, baby food, toddler snack puffs,
  bottles and bottle brushes, pacifiers, strollers, car seats, crib mattresses, baby
  monitors. Kids' versions of adult products (children's medicine, kids' clothing/shoes)
  → the adult product's category; toys →Toys & Games.
- **Pet Supplies** — Food and gear for ANY animal. Pet-specific always wins (global rule
  3), incl. pet car gear and wild bird seed/feeders.

### Leisure & misc

- **Electronics** — Personal tech and AV devices plus accessories — phones, device
  chargers/cables, headphones, smart-home hubs/cameras/speakers, smartwatches, power
  banks, game consoles, video games, digital photo frames. Smart plugs and light bulbs of
  ANY kind →Lighting & Electrical; baby monitors →Baby & Kids; household machines
  →Appliances.
- **Toys & Games** — Indoor physical toys, board games, jigsaw puzzles for any age.
  Large outdoor play equipment (trampolines, swing sets), pool/beach inflatables,
  floaties, wheeled ride-ons →Sports & Outdoors; activity books →Books & Media; drawing
  materials →Arts, Crafts & Hobbies; video games →Electronics; pet toys →Pet Supplies.
- **Books & Media** — ALL printed books incl. coloring, crossword, activity books, plus
  magazines, newspapers, physical music/film. Video games →Electronics; blank lined
  notebooks →Office & Stationery; sketchbooks →Arts, Crafts & Hobbies.
- **Sports & Outdoors** — Fitness, sports, camping, winter, beach, picnic gear — bikes,
  scooters, skates, helmets, tents, sleeping bags, air mattresses, cooler bags,
  binoculars, disposable hand warmers, trampolines, pool inflatables/floaties, sport
  wearables (wetsuits, ski gloves, swim goggles). Beach towels →Bedding & Bath; beach
  umbrellas →Garden & Outdoor; activewear →Clothing; sneakers →Shoes & Footwear.
- **Office & Stationery** — Writing, paper, desk supplies — pens, pencils, lined/plain
  notebooks, printer ink/paper, envelopes, stamps, office tape, glue sticks. Sketchbooks
  and coloring/drawing instruments →Arts, Crafts & Hobbies; greeting cards and gift wrap
  →Party, Gifts & Holiday.
- **Arts, Crafts & Hobbies** — Craft/hobby materials, ALL coloring/drawing instruments,
  sketchbooks/drawing pads — yarn, sewing supplies, beads, artist paints, glue guns,
  model kits, crayons, sidewalk chalk, plus musical instruments and accessories. Spray
  paint and wall paint →Hardware & Tools; sewing machines →Appliances; plain writing pens
  →Office & Stationery.
- **Party, Gifts & Holiday** — Celebration and holiday supplies of ANY theme — gift
  wrap, greeting cards, balloons, birthday candles, party/holiday decorations, Christmas
  lights, ornaments, advent calendars, costumes. Everyday decor and non-holiday candles
  →Home Decor; plain disposable tableware →Paper & Disposables; the gift item itself goes
  to its own category.
- **Tobacco & Vaping** — ALL recreational tobacco and nicotine — cigarettes, cigars,
  vapes, nicotine pouches, snus, rolling papers. Cessation-labeled nicotine gum/patches
  →Health & Pharmacy; matches/lighters →Household & Cleaning.
- **Other** — Catch-all of last resort — services, vouchers, one-off oddities (gift
  card, lottery ticket, key cutting). If an item fits any other category, it never goes
  here.

## Consequence for auto-categorization

Several ties are resolved by **intended use**, which the words alone don't reveal —
"cleaning vinegar" vs "cooking vinegar" are the same string with different homes only by
label. The LLM categorizer can read these rules and resolve them. A **keyword seed map
cannot**, and must not try — it should map only unambiguous items and leave the
intent-collision cases uncategorized (or default to the more common home). Encoding
intent into a keyword map produces confident wrong answers.

## Rejected alternatives

- **~18 categories** — too coarse for a general shopping app; clothing and kitchenware
  had no real home.
- **Cuisine/origin categories** ("International Foods", "Asian") — every item in them
  also belongs somewhere by ingredient, so they guarantee overlap.
- **Keeping user-created custom categories** — breaks auto-categorization (no algorithm
  can guess a user's private category) and complicates shared-list sync (categories would
  have to sync too). A fixed set means categories never sync. `Other` is the escape
  hatch. Custom categories can return later as local-only decoration if users demand it.
