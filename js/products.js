// Nugfinity - Luxury Mandap Decor, Crystal Jhumars & Royal Event Lighting
// Pure Informational & Event Showcase Database (NO E-Commerce / No Buy System)
const WHATSAPP_PHONE = "7505045948";

// Reliable SVG Data-URIs for Guaranteed Fallback (Zero missing images even if offline)
const FALLBACK_IMAGES = {
  jhumar: `data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="800" height="600" viewBox="0 0 800 600"><defs><linearGradient id="g" x1="0%" y1="0%" x2="100%" y2="100%"><stop offset="0%" stop-color="%231E170B"/><stop offset="50%" stop-color="%230E131F"/><stop offset="100%" stop-color="%23070A10"/></linearGradient><radialGradient id="glow" cx="50%" cy="40%" r="50%"><stop offset="0%" stop-color="%23F59E0B" stop-opacity="0.35"/><stop offset="100%" stop-color="%23000" stop-opacity="0"/></radialGradient></defs><rect width="800" height="600" fill="url(%23g)"/><circle cx="400" cy="240" r="220" fill="url(%23glow)"/><g fill="none" stroke="%23FBBF24" stroke-width="2" opacity="0.85"><path d="M400,60 L400,120 M320,120 L480,120 M260,180 L540,180 M200,260 L600,260 M280,360 L520,360 M340,440 L460,440 M400,500 L400,520"/><polygon points="400,120 320,180 480,180" fill="%23F59E0B" fill-opacity="0.2"/><polygon points="400,180 260,260 540,260" fill="%23F59E0B" fill-opacity="0.15"/><polygon points="400,260 280,360 520,360" fill="%23F59E0B" fill-opacity="0.25"/><polygon points="400,360 340,440 460,440" fill="%23F59E0B" fill-opacity="0.3"/></g><text x="400" y="555" fill="%23FDE68A" font-family="serif" font-size="22" font-weight="bold" text-anchor="middle" letter-spacing="3">NUGFINITY GRAND CRYSTAL JHUMAR</text></svg>`,
  
  sofa: `data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="800" height="600" viewBox="0 0 800 600"><defs><linearGradient id="g2" x1="0%" y1="0%" x2="100%" y2="100%"><stop offset="0%" stop-color="%2322130A"/><stop offset="50%" stop-color="%23120E1A"/><stop offset="100%" stop-color="%23070A10"/></linearGradient><radialGradient id="glow2" cx="50%" cy="50%" r="50%"><stop offset="0%" stop-color="%23D97706" stop-opacity="0.3"/><stop offset="100%" stop-color="%23000" stop-opacity="0"/></radialGradient></defs><rect width="800" height="600" fill="url(%23g2)"/><circle cx="400" cy="300" r="240" fill="url(%23glow2)"/><g fill="none" stroke="%23F59E0B" stroke-width="3" opacity="0.85"><path d="M180,420 L620,420 C640,420 660,400 660,370 L660,320 C660,280 630,240 590,240 L560,240 C560,180 500,140 400,140 C300,140 240,180 240,240 L210,240 C170,240 140,280 140,320 L140,370 C140,400 160,420 180,420 Z" fill="%2378350F" fill-opacity="0.4"/><line x1="200" y1="420" x2="190" y2="480"/><line x1="600" y1="420" x2="610" y2="480"/><line x1="330" y1="420" x2="330" y2="480"/><line x1="470" y1="420" x2="470" y2="480"/></g><text x="400" y="555" fill="%23FDE68A" font-family="serif" font-size="22" font-weight="bold" text-anchor="middle" letter-spacing="3">MAHARAJA ROYAL STAGE SOFA</text></svg>`,
  
  tree: `data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="800" height="600" viewBox="0 0 800 600"><defs><linearGradient id="g3" x1="0%" y1="0%" x2="100%" y2="100%"><stop offset="0%" stop-color="%2309191C"/><stop offset="50%" stop-color="%230E1424"/><stop offset="100%" stop-color="%2307090E"/></linearGradient><radialGradient id="glow3" cx="50%" cy="35%" r="50%"><stop offset="0%" stop-color="%2306B6D4" stop-opacity="0.25"/><stop offset="100%" stop-color="%23000" stop-opacity="0"/></radialGradient></defs><rect width="800" height="600" fill="url(%23g3)"/><circle cx="400" cy="260" r="250" fill="url(%23glow3)"/><g stroke="%23FDE68A" stroke-width="2" opacity="0.85"><path d="M400,520 Q400,380 390,300 Q360,220 300,160 M390,300 Q430,220 480,150 M400,520 Q410,400 440,320 Q480,260 560,220 M390,340 Q330,280 240,240" fill="none" stroke="%23B45309" stroke-width="8"/><circle cx="300" cy="160" r="4" fill="%23FDE68A"/><circle cx="480" cy="150" r="4" fill="%23FDE68A"/><circle cx="560" cy="220" r="4" fill="%23FDE68A"/><circle cx="240" cy="240" r="4" fill="%23FDE68A"/><circle cx="350" cy="190" r="3" fill="%2306B6D4"/><circle cx="440" cy="180" r="3" fill="%2306B6D4"/><circle cx="380" cy="130" r="4" fill="%23FDE68A"/><circle cx="420" cy="110" r="4" fill="%23FDE68A"/></g><text x="400" y="555" fill="%2367E8F9" font-family="serif" font-size="22" font-weight="bold" text-anchor="middle" letter-spacing="3">12FT GIANT ILLUMINATED TREE LAMP</text></svg>`,
  
  pillar: `data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="800" height="600" viewBox="0 0 800 600"><defs><linearGradient id="g4" x1="0%" y1="0%" x2="100%" y2="100%"><stop offset="0%" stop-color="%231E120A"/><stop offset="50%" stop-color="%23120E18"/><stop offset="100%" stop-color="%23070A10"/></linearGradient><radialGradient id="glow4" cx="50%" cy="50%" r="50%"><stop offset="0%" stop-color="%23F59E0B" stop-opacity="0.3"/><stop offset="100%" stop-color="%23000" stop-opacity="0"/></radialGradient></defs><rect width="800" height="600" fill="url(%23g4)"/><circle cx="400" cy="300" r="220" fill="url(%23glow4)"/><g fill="none" stroke="%23FBBF24" stroke-width="3" opacity="0.85"><rect x="340" y="100" width="120" height="380" rx="6" fill="%2378350F" fill-opacity="0.3"/><rect x="310" y="480" width="180" height="40" rx="4" fill="%2392400E" fill-opacity="0.5"/><rect x="310" y="70" width="180" height="30" rx="4" fill="%2392400E" fill-opacity="0.5"/><circle cx="400" cy="200" r="25"/><circle cx="400" cy="290" r="25"/><circle cx="400" cy="380" r="25"/></g><text x="400" y="555" fill="%23FDE68A" font-family="serif" font-size="22" font-weight="bold" text-anchor="middle" letter-spacing="3">10FT BRASS MANDAP PILLARS</text></svg>`
};

const PRODUCTS_DATA = [
  {
    id: "nug-01",
    name: "Imperial 12-Foot Royal K9 Crystal Mandap Jhumar",
    hindiName: "इंपीरियल 12-फीट रॉयल क्रिस्टल मंडप झूमर",
    category: "crystal-jhumar",
    categoryLabel: "Grand Crystal Jhumars",
    propType: "Mandap Ceiling Centerpiece",
    typeKey: "jhumar",
    material: "Pure K9 Optical Crystal & 24K Gold Gilt Heavy Steel Frame",
    height: "12 Feet (3.65 m Drop)",
    diameter: "6.5 Feet Width (2.0 m)",
    lightsCount: "48 DMX Dimmable Warm Amber Sockets (Included)",
    weight: "85 kg (Equipped with safety rigging points)",
    availability: "Event Rental & Direct Factory Order",
    badge: "Mandap Showstopper",
    image: "https://images.unsplash.com/photo-1531219436348-af71f9a82649?auto=format&fit=crop&w=900&q=80",
    fallback: FALLBACK_IMAGES.jhumar,
    idealFor: "Grand Wedding Mandap, Palace Courtyard, Varmala Center Stage",
    description: "Nugfinity's signature monumental 12ft chandelier designed for high-profile Indian weddings. Strung with 4,500+ faceted K9 crystal prisms that project kaleidoscopic golden light across the sacred mandap ceremony.",
    specifications: [
      { label: "Total Vertical Drop", val: "12 Feet (3.65 Meters)" },
      { label: "Outer Diameter", val: "6.5 Feet (2.0 Meters)" },
      { label: "Crystal Grade", val: "Optical K9 Precision Cut Prisms" },
      { label: "Electrical Rigging", val: "48 Dimmable Warm Amber LEDs + Master DMX Controller" },
      { label: "Transit Case", val: "Heavy Duty Shock-Proof Flight Case Included" }
    ],
    highlights: [
      "Monumental 12ft drop transforms any high truss or palace roof into royalty",
      "Compatible with motorized chain hoists for seamless on-site raising",
      "High refractive crystal index ensuring brilliant sparkles in photography",
      "Quick modular crystal strand rigging for rapid 45-minute assembly"
    ]
  },
  {
    id: "nug-02",
    name: "Palace Spiral Waterfall Cascade Crystal Chandelier",
    hindiName: "पैलेस स्पाइरल वॉटरफॉल क्रिस्टल झूमर",
    category: "crystal-jhumar",
    categoryLabel: "Grand Crystal Jhumars",
    propType: "Ceiling Spiral Cascade",
    typeKey: "jhumar",
    material: "High-Luster Prismatic Crystals & Titanium Gold Mirror Base",
    height: "9.5 Feet (2.9 m Drop)",
    diameter: "4.5 Feet Width (1.37 m)",
    lightsCount: "32 High-Lumen Focused Warm Spots",
    weight: "55 kg",
    availability: "Event Rental & Direct Factory Order",
    badge: "VIP Stage Decor",
    image: "https://images.unsplash.com/photo-1573055458950-24b45efec1ee?auto=format&fit=crop&w=900&q=80",
    fallback: FALLBACK_IMAGES.jhumar,
    idealFor: "Barat Reception Entry Tunnel, Cocktail Lounge, Sangeet Dance Truss",
    description: "A continuous spiraling helical waterfall of optical crystal strands that appears like frozen liquid light descending from the ceiling. A premier choice for VIP entryway arches and banquet dance floors.",
    specifications: [
      { label: "Vertical Drop", val: "9.5 Feet (2.9 Meters)" },
      { label: "Base Canopy", val: "Titanium Gold Stainless Steel Mirror Plate" },
      { label: "Crystal Design", val: "Helical Downward Spiral Waterfall" },
      { label: "Suspension", val: "Aircraft Grade Stainless Wire Strands" }
    ],
    highlights: [
      "Dynamic flowing helix silhouette creates natural visual motion",
      "Reflects double the sparkle through its mirror-polished ceiling plinth",
      "Pre-strung modular segments for fast banquet installation",
      "Designed specifically for luxury event staging & photography"
    ]
  },
  {
    id: "nug-03",
    name: "Maharaja Teakwood Hand-Carved Mandap Stage Sofa",
    hindiName: "महाराजा सागवान नक्काशीदार मंडप स्टेज सोफा",
    category: "royal-sofa",
    categoryLabel: "Royal Stage Furniture",
    propType: "Stage Maharaja Seating",
    typeKey: "sofa",
    material: "Seasoned Indian Teakwood & 24K Pure Gold Leafing",
    height: "5.2 Feet Backrest",
    diameter: "8.5 Feet Width (Spacious 3-4 Seater)",
    lightsCount: "Concealed Warm Gold Under-Stage Halo (Optional)",
    weight: "72 kg Solid Timber",
    availability: "Event Rental & Custom Woodcraft Order",
    badge: "Royal Maharaja Class",
    image: "https://images.unsplash.com/photo-1555041469-a586c61ea9bc?auto=format&fit=crop&w=900&q=80",
    fallback: FALLBACK_IMAGES.sofa,
    idealFor: "Bride & Groom Varmala Stage, Reception Backdrop, Mandap Hawan Seating",
    description: "Chiseled from solid seasoned teakwood by master artisans, adorned with traditional royal crest carvings and micro-gilded 24K gold foil. Upholstered in high-density stain-resistant royal jewel velvet.",
    specifications: [
      { label: "Timber Quality", val: "100% Solid Seasoned Teakwood (Sagwan)" },
      { label: "Finish Style", val: "Hand-Gilded 24K Gold Leaf Foil Patina" },
      { label: "Upholstery", val: "Spill-Proof Heavy Jewel Velvet with Deep Buttoning" },
      { label: "Dimensions", val: "8.5 ft Width x 5.2 ft Backrest Height" }
    ],
    highlights: [
      "Commanding royal silhouette guarantees regal bride & groom stage photography",
      "Reinforced heavy-duty frame withstands continuous high-profile event usage",
      "Gold leaf reflects stage spotlighting without glare or color distortion",
      "Ergonomically cushioned for multi-hour ceremony comfort"
    ]
  },
  {
    id: "nug-04",
    name: "Imperial Victorian Couple Throne Chairs (Pair)",
    hindiName: "इंपीरियल विक्टोरियन कपल थ्रोन कुर्सियां (जोड़ी)",
    category: "royal-sofa",
    categoryLabel: "Royal Stage Furniture",
    propType: "Twin Throne Set",
    typeKey: "sofa",
    material: "Carved Hardwood, Antique Gold Leaf & Ivory Damask Silk",
    height: "5.8 Feet High Backrest",
    diameter: "3 Feet Width per Chair",
    lightsCount: "N/A",
    weight: "28 kg per Chair",
    availability: "Event Rental & Direct Factory Order",
    badge: "Bridal Favorite",
    image: "https://images.unsplash.com/photo-1586023492125-27b2c045efd7?auto=format&fit=crop&w=900&q=80",
    fallback: FALLBACK_IMAGES.sofa,
    idealFor: "Sangeet Night, Engagement Ceremony, Mandap Seating, Royal Photoshoot",
    description: "Twin grand thrones featuring towering carved crown crests and ornate armrests. Designed to frame the royal couple with majestic elegance during engagement, sangeet, and mandap rituals.",
    specifications: [
      { label: "Backrest Elevation", val: "5.8 Feet Towering Crown Crest" },
      { label: "Frame Craft", val: "Chiseled Sheesham & Antique Gold Finish" },
      { label: "Cushioning", val: "High-Resilience Orthopedic Density Foam" },
      { label: "Included Accessories", val: "2 Matching Carved Royal Footrests" }
    ],
    highlights: [
      "Provides distinct, regal separation for individual couple portraits",
      "Stately 5.8ft crown height ensures visible presence on elevated stages",
      "Scratch-resistant gold seal for frequent destination event transit",
      "Timeless European & Rajwada fusion aesthetic"
    ]
  },
  {
    id: "nug-05",
    name: "12-Foot Giant Golden Willow LED Tree Lamp",
    hindiName: "12-फीट विशाल गोल्डन विलो एलईडी ट्री लैंप",
    category: "tree-lamp",
    categoryLabel: "Giant Illuminated Tree Lamps",
    propType: "Monumental Ped Lamp / Fairy Tree",
    typeKey: "tree",
    material: "Flexible Molded Realistic Bark Trunk & 3,200 Waterproof Fairy LEDs",
    height: "12 Feet Height (3.65 m)",
    diameter: "9.8 Feet Broad Canopy (3.0 m)",
    lightsCount: "3,200 Amber Gold Micro Fairy LEDs (IP65 Waterproof)",
    weight: "68 kg with Heavy Weighted Plinth",
    availability: "Event Rental & Direct Factory Order",
    badge: "Grand Entrance Prop",
    image: "https://images.unsplash.com/photo-1513297887119-d46091b24bfa?auto=format&fit=crop&w=900&q=80",
    fallback: FALLBACK_IMAGES.tree,
    idealFor: "Barat Entry Pathway, Mandap Corner Framing, Farmhouse Lawn Galas",
    description: "A colossal 12-foot weeping willow tree lamp featuring 3,200+ cascading micro-fairy lights. Transforms outdoor wedding lawns and entrance pathways into an enchanted golden forest.",
    specifications: [
      { label: "Height & Canopy", val: "12 ft Height x 9.8 ft Canopy Spread" },
      { label: "LED Density", val: "3,200 High-Brightness Amber Glow Micro LEDs" },
      { label: "Weatherproofing", val: "Commercial IP65 Rating (Rain & Dew Resistant)" },
      { label: "Power Supply", val: "Safe 24V Heavy Duty Commercial Adapter Included" }
    ],
    highlights: [
      "Massive 12ft scale creates unforgettable wedding guest entrance visual impact",
      "Waterproof outdoor build engineered for open-air farmhouses and lawns",
      "Ultra-realistic bark texture indistinguishable from natural trees",
      "Weighted steel base ensures absolute wind and crowd stability"
    ]
  },
  {
    id: "nug-06",
    name: "10-Foot Imperial White Cherry Blossom Light Tree",
    hindiName: "10-फीट इंपीरियल चेरी ब्लॉसम लाइट ट्री",
    category: "tree-lamp",
    categoryLabel: "Giant Illuminated Tree Lamps",
    propType: "Ped Lamp / Blossom Tree",
    typeKey: "tree",
    material: "Realistic Molded Trunk & Translucent Silicone Flower Petal LEDs",
    height: "10 Feet Height (3.0 m)",
    diameter: "8 Feet Canopy Spread (2.4 m)",
    lightsCount: "2,400 Dual Warm & Soft White Petal LEDs",
    weight: "48 kg",
    availability: "Event Rental & Direct Factory Order",
    badge: "Trending Decor",
    image: "https://images.unsplash.com/photo-1543258108-4309c6df8110?auto=format&fit=crop&w=900&q=80",
    fallback: FALLBACK_IMAGES.tree,
    idealFor: "Cocktail Gala, Mehendi Garden Canopy, VIP Lounge, Stage Framing",
    description: "Ethereal illuminated white cherry blossoms that radiate pure soft light. Branches are flexible and can be shaped into romantic floral arches for VIP walkway corridors and photo-booths.",
    specifications: [
      { label: "Total Height", val: "10 Feet (3.0 Meters)" },
      { label: "Petal Craft", val: "Molded Translucent Soft Silicone Blossoms" },
      { label: "LED Count", val: "2,400 Micro Diodes inside Flower Petals" },
      { label: "Assembly", val: "Quick Detachable Quick-Lock Branch Structure" }
    ],
    highlights: [
      "Soft ambient illumination that casts zero glare on guest cameras",
      "Flexible branches allow custom canopy shaping for venue constraints",
      "Modular branch setup allows single-person 20-minute setup",
      "Breathtaking visual contrast against dark evening backdrops"
    ]
  },
  {
    id: "nug-07",
    name: "Royal 5-Tier Infinity Ring Crystal Chandelier",
    hindiName: "रॉयल 5-टियर इन्फिनिटी रिंग क्रिस्टल झूमर",
    category: "crystal-jhumar",
    categoryLabel: "Grand Crystal Jhumars",
    propType: "Modern Statement Ring Cluster",
    typeKey: "jhumar",
    material: "Titanium Gold Stainless Steel & High-Refraction Crystal Baguettes",
    height: "8 Feet Max Adjustable Drop",
    diameter: "5.5 Feet Widest Outer Ring",
    lightsCount: "Built-In Remote Controlled 3000K-4000K LED Strips",
    weight: "42 kg",
    availability: "Event Rental & Direct Factory Order",
    badge: "Modern Luxury",
    image: "https://images.unsplash.com/photo-1643099958315-bbf07e4d5881?auto=format&fit=crop&w=900&q=80",
    fallback: FALLBACK_IMAGES.jhumar,
    idealFor: "Modern Mandap Ceilings, Sangeet Night Center Truss, Atrium Hangings",
    description: "Five interlocking concentric infinity rings encased in multifaceted crystal baguettes. Rings can be tilted and arranged into spheres, spirals, or flat nested crowns via adjustable suspension cables.",
    specifications: [
      { label: "Ring Configuration", val: "5 Concentric Interlocking Rings (5.5ft down to 1.5ft)" },
      { label: "Lighting Tech", val: "Integrated Dual-Tone Dimmable LED Arrays" },
      { label: "Controller", val: "Wireless RF Remote + DMX Protocol Support" },
      { label: "Cable Rigging", val: "High-Tensile Aircraft Steel Cable Grippers" }
    ],
    highlights: [
      "Can be re-configured into different 3D geometric shapes for each event",
      "Low power consumption with high lumen output for indoor banquets",
      "Zero breakable glass strings—engineered with crystal baguettes",
      "Sleek contemporary look favored by luxury wedding designers"
    ]
  },
  {
    id: "nug-08",
    name: "Rajwada Antique Carved Wedding Chaise Diwan",
    hindiName: "रजवाड़ा विंटेज नक्काशीदार वेडिंग दीवान",
    category: "royal-sofa",
    categoryLabel: "Royal Stage Furniture",
    propType: "Traditional Stage Diwan",
    typeKey: "sofa",
    material: "Hand-Carved Indian Sheesham Timber with Antique Gold Foil Patina",
    height: "3.8 Feet",
    diameter: "7.2 Feet Length",
    lightsCount: "N/A",
    weight: "45 kg",
    availability: "Event Rental & Custom Woodcraft Order",
    badge: "Heritage Craft",
    image: "https://images.unsplash.com/photo-1618221195710-dd6b41faaea6?auto=format&fit=crop&w=900&q=80",
    fallback: FALLBACK_IMAGES.sofa,
    idealFor: "Haldi & Mehendi Ceremomy, Mandap Hawan Seating, Heritage Palace Weddings",
    description: "An authentic low-profile royal chaise lounge carved with Rajasthani jharokha arches, accompanied by traditional cylindrical bolsters and raw silk brocade cushioning.",
    specifications: [
      { label: "Timber", val: "Seasoned Indian Sheesham Hardwood" },
      { label: "Carving Motifs", val: "Rajasthani Floral & Arch Jali Elements" },
      { label: "Includes", val: "2 Cylindrical Bolsters + 4 Silk Throw Pillows" },
      { label: "Seat Clearance", val: "Low Profile for Unobstructed Fire Hawan Visibility" }
    ],
    highlights: [
      "Provides traditional cultural grandeur for sacred rituals",
      "Does not block audience view of the holy fire ceremony",
      "Deeply seasoned timber resistant to thermal expansion",
      "Stunning pairing with brass urlis and marigold floral decor"
    ]
  },
  {
    id: "nug-09",
    name: "10-Foot Pierced Brass Mandap Pillars (Set of 4)",
    hindiName: "10-फीट जालीदार पीतल मंडप पिलर्स (4 का सेट)",
    category: "mandap-pillars",
    categoryLabel: "Mandap Pillars & Entry Architecture",
    propType: "Architectural Mandap Framework",
    typeKey: "pillar",
    material: "Heavy-Gauge Solid Brass with Hand-Pierced Jaali Lattice",
    height: "10 Feet (3.0 m)",
    diameter: "18 Inches Column Diameter",
    lightsCount: "4 Internal 360-Degree Amber Projector Spotlights (Included)",
    weight: "140 kg (Total Set of 4 Pillars)",
    availability: "Event Rental & Direct Factory Order",
    badge: "Master Mandap Structure",
    image: "https://images.unsplash.com/photo-1540932239986-30128078f3c5?auto=format&fit=crop&w=900&q=80",
    fallback: FALLBACK_IMAGES.pillar,
    idealFor: "Complete Sacred 4-Pillar Mandap Pavilion, Grand Barat Welcomes",
    description: "A monumental four-pillar architectural mandap system hand-chiseled from thick sheet brass. Internal lighting projects intricate radial shadow lace across the entire wedding platform and ceiling.",
    specifications: [
      { label: "Column Dimensions", val: "10 Feet Height x 18 Inches Diameter each" },
      { label: "Structural Support", val: "Load-Bearing Internal Steel Core for Roof Garlands" },
      { label: "Internal Projector", val: "4 High-Output 360-Degree 2700K Warm Spots" },
      { label: "Base Plinths", val: "Heavy Weighted Brass Flanged Footing" }
    ],
    highlights: [
      "Complete 4-pillar architectural foundation for royal wedding mandaps",
      "Internal illumination creates dramatic palace-like shadow art on venue walls",
      "Sturdy enough to support heavy floral ceiling cascades and crystal chandeliers",
      "Corrosion-sealed brass retains mirror shine across multiple wedding seasons"
    ]
  },
  {
    id: "nug-10",
    name: "Grand Royal Brass Welcome Urli Diya Chandelier Stand",
    hindiName: "भव्य पीतल वेलकम उरली व दीया झूमर स्टैंड",
    category: "mandap-pillars",
    categoryLabel: "Mandap Pillars & Entry Architecture",
    propType: "Ceremonial Welcoming Feature",
    typeKey: "pillar",
    material: "Cast Heavy Solid Brass with Fluted Floral Water Basins",
    height: "6.5 Feet (2.0 m)",
    diameter: "3.5 Feet Base Basin Width",
    lightsCount: "Accommodates 108 Traditional/LED Diyas + Warm Accent Base",
    weight: "36 kg",
    availability: "Event Rental & Direct Factory Order",
    badge: "Auspicious Welcome",
    image: "https://images.unsplash.com/photo-1511795409834-ef04bbd61622?auto=format&fit=crop&w=900&q=80",
    fallback: FALLBACK_IMAGES.pillar,
    idealFor: "Barat Reception Welcoming Gate, Mandap Pathway Steps, Hotel Atrium Entry",
    description: "A ceremonial multi-tiered brass urli fountain and diya tower designed for floating natural lotus flowers and rose petals, flanked by radiant warm amber illumination.",
    specifications: [
      { label: "Total Height", val: "6.5 Feet (2.0 Meters)" },
      { label: "Bowl Diameter", val: "3.5 Feet Wide Heavy Water Basin" },
      { label: "Diya Capacity", val: "108 Oil / Battery Warm LED Diya Receptacles" },
      { label: "Material", val: "100% Solid Cast Brass with Antique Gold Treatment" }
    ],
    highlights: [
      "Auspicious traditional ceremonial welcome for royal Indian weddings",
      "Deep water basin holds floating flower arrangements and floating candles",
      "Heavy cast brass ensures stability against guest traffic",
      "Instant luxury visual hook at venue entrance doorways"
    ]
  },
  {
    id: "nug-11",
    name: "8-Foot Illuminated Crystal Enchanted Birch Tree",
    hindiName: "8-फीट क्रिस्टल एन्चांटेड बिर्च लाइट ट्री",
    category: "tree-lamp",
    categoryLabel: "Giant Illuminated Tree Lamps",
    propType: "Ped Lamp / Crystal Tree",
    typeKey: "tree",
    material: "Textured Realistic White Birch Trunk & Optical Acrylic Crystal Leaves",
    height: "8 Feet Height (2.45 m)",
    diameter: "6.5 Feet Canopy Spread (2.0 m)",
    lightsCount: "1,800 Sparkling Warm White Micro LEDs",
    weight: "32 kg",
    availability: "Event Rental & Direct Factory Order",
    badge: "Fairytale Vibe",
    image: "https://images.unsplash.com/photo-1482575832494-771f74bf6857?auto=format&fit=crop&w=900&q=80",
    fallback: FALLBACK_IMAGES.tree,
    idealFor: "Indoor Banquet Stages, Walkway Borders, Photo-Booth Corners, Cake Stages",
    description: "Sculpted with realistic birch bark and crystal-faceted leaves that catch ambient room light by day and cast a soothing fairytale glow by night. Ideal for indoor ballroom staging.",
    specifications: [
      { label: "Height & Width", val: "8 ft Height x 6.5 ft Canopy Spread" },
      { label: "Leaf Details", val: "Faceted Acrylic Optical Crystals on every Branch" },
      { label: "LED Array", val: "1,800 Micro White/Warm Diodes" },
      { label: "Power Draw", val: "Ultra Low Energy under 80W" }
    ],
    highlights: [
      "Slimmer footprint perfect for flanking wedding stage risers",
      "Faceted crystal leaves magnify and refract diode sparkle",
      "Safe touch low-voltage internal cabling",
      "Includes weighted decorative floor plinth cover"
    ]
  },
  {
    id: "nug-12",
    name: "Belgian Cut-Glass Grand Canopy Jhumar Array (Truss Grid)",
    hindiName: "बेल्जियम कट-ग्लास ग्रैंड कैनोपी झूमर ट्रस ग्रिड",
    category: "crystal-jhumar",
    categoryLabel: "Grand Crystal Jhumars",
    propType: "Monumental Ceiling Grid Array",
    typeKey: "jhumar",
    material: "Octagonal Cut Belgian Glass Prisms & Heavy Modular Truss Brackets",
    height: "6 Feet Drop per Chandelier (Module Set of 6)",
    diameter: "Customizable 20ft x 20ft Coverage Area",
    lightsCount: "72 Synchronized DMX Dimmable Warm Halos",
    weight: "180 kg Total Grid System",
    availability: "Event Production & Rigging Rental",
    badge: "Monumental Stage Package",
    image: "https://images.unsplash.com/photo-1519741497674-611481863552?auto=format&fit=crop&w=900&q=80",
    fallback: FALLBACK_IMAGES.jhumar,
    idealFor: "Grand Destination Weddings, Sangeet Main Stage Truss, Palace Courtyards",
    description: "A complete professional modular ceiling canopy grid of 6 synchronized Belgian crystal chandeliers designed to blanket an entire wedding mandap or dance floor under a shimmering crystal roof.",
    specifications: [
      { label: "Array Format", val: "6 Linked Modular Chandeliers (6ft Drop each)" },
      { label: "Ceiling Coverage", val: "Up to 400 sq.ft (20ft x 20ft Grid)" },
      { label: "Controller Protocol", val: "Synchronized DMX Intelligent Dimming" },
      { label: "Rigging Hardware", val: "Heavy Duty Half-Coupler Truss Clamps Included" }
    ],
    highlights: [
      "Transforms empty truss rafters into a breathtaking palace ballroom ceiling",
      "Enables dynamic lighting fades during bride & groom Varmala moments",
      "Engineered specifically for luxury wedding production houses and event designers",
      "Furnished in custom heavy-duty wheeled road cases for rapid setup"
    ]
  }
];

// Categories definition
const CATEGORIES_DATA = [
  { id: "all", label: "All Grand Props", count: 12 },
  { id: "crystal-jhumar", label: "Grand Crystal Jhumars", count: 4 },
  { id: "royal-sofa", label: "Royal Stage Sofas", count: 3 },
  { id: "tree-lamp", label: "Giant Tree Lamps", count: 3 },
  { id: "mandap-pillars", label: "Mandap Pillars & Entry", count: 2 }
];

// Testimonials Data
const TESTIMONIALS_DATA = [
  {
    name: "Kabir Malhotra",
    city: "New Delhi",
    role: "Lead Designer, The Royal Knot Events",
    rating: 5,
    title: "The 12ft Mandap Jhumar made our Fairmont Jaipur wedding unforgettable!",
    comment: "We booked the 12ft Imperial Crystal Jhumar and four 12ft Golden Willow Tree lamps. The crystal refraction and build quality are phenomenal. Nugfinity team handled prompt dispatch and direct coordination on 7505045948 was super smooth!",
    product: "Imperial 12ft Royal Crystal Mandap Jhumar + Golden Willow Tree",
    avatar: "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=150&q=80"
  },
  {
    name: "Simran & Harpreet Chawla",
    city: "Chandigarh",
    role: "Bride & Groom",
    rating: 5,
    title: "Our stage photographs look like a royal Maharaja palace!",
    comment: "The Teakwood Maharaja Stage Sofa and the Crystal Cascade Jhumar took our Varmala stage to another level. Everyone in our family and guest list was in awe. Direct WhatsApp support on 7505045948 was super fast!",
    product: "Maharaja Teakwood Mandap Stage Sofa",
    avatar: "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=150&q=80"
  },
  {
    name: "Devendra Rathore",
    city: "Udaipur",
    role: "Director, Rathore Palace Decorators",
    rating: 5,
    title: "Best investment for our event inventory in years",
    comment: "Purchased two 10ft Pierced Brass Pillars and the 5-Ring Infinity Chandelier for our event rental inventory. Solid, heavy-gauge commercial grade quality that holds up to frequent transport. Highly recommend Nugfinity!",
    product: "10-Foot Pierced Brass Mandap Pillars (Set of 4)",
    avatar: "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=150&q=80"
  }
];
