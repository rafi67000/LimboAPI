/*
 * Copyright (C) 2021 Elytrium
 *
 * The LimboAPI (excluding the LimboAPI plugin) is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package net.elytrium.limboapi.api.material;

public enum Block {
  AIR(0),
  STONE(1),
  GRASS(2),
  DIRT(3),
  COBBLESTONE(4),
  PLANKS(5),
  SAPLING(6),
  BEDROCK(7),
  FLOWING_WATER(8),
  WATER(9),
  FLOWING_LAVA(10),
  LAVA(11),
  SAND(12),
  GRAVEL(13),
  GOLD_ORE(14),
  IRON_ORE(15),
  COAL_ORE(16),
  LOG(17),
  LEAVES(18),
  SPONGE(19),
  GLASS(20),
  LAPIS_ORE(21),
  LAPIS_BLOCK(22),
  DISPENSER(23),
  SANDSTONE(24),
  NOTEBLOCK(25),
  BED(26),
  GOLDEN_RAIL(27),
  DETECTOR_RAIL(28),
  STICKY_PISTON(29),
  WEB(30),
  TALLGRASS(31),
  DEADBUSH(32),
  PISTON(33),
  PISTON_HEAD(34),
  WOOL(35),
  PISTON_EXTENSION(36),
  YELLOW_FLOWER(37),
  RED_FLOWER(38),
  BROWN_MUSHROOM(39),
  RED_MUSHROOM(40),
  GOLD_BLOCK(41),
  IRON_BLOCK(42),
  DOUBLE_STONE_SLAB(43),
  STONE_SLAB(44),
  BRICK_BLOCK(45),
  TNT(46),
  BOOKSHELF(47),
  MOSSY_COBBLESTONE(48),
  OBSIDIAN(49),
  TORCH(50),
  FIRE(51),
  MOB_SPAWNER(52),
  OAK_STAIRS(53),
  CHEST(54),
  REDSTONE_WIRE(55),
  DIAMOND_ORE(56),
  DIAMOND_BLOCK(57),
  CRAFTING_TABLE(58),
  WHEAT(59),
  FARMLAND(60),
  FURNACE(61),
  LIT_FURNACE(62),
  STANDING_SIGN(63),
  WOODEN_DOOR(64),
  LADDER(65),
  RAIL(66),
  STONE_STAIRS(67),
  WALL_SIGN(68),
  LEVER(69),
  STONE_PRESSURE_PLATE(70),
  IRON_DOOR(71),
  WOODEN_PRESSURE_PLATE(72),
  REDSTONE_ORE(73),
  LIT_REDSTONE_ORE(74),
  UNLIT_REDSTONE_TORCH(75),
  REDSTONE_TORCH(76),
  STONE_BUTTON(77),
  SNOW_LAYER(78),
  ICE(79),
  SNOW(80),
  CACTUS(81),
  CLAY(82),
  REEDS(83),
  JUKEBOX(84),
  FENCE(85),
  PUMPKIN(86),
  NETHERRACK(87),
  SOUL_SAND(88),
  GLOWSTONE(89),
  PORTAL(90),
  LIT_PUMPKIN(91),
  CAKE(92),
  UNPOWERED_REPEATER(93),
  POWERED_REPEATER(94),
  STAINED_GLASS(95),
  TRAPDOOR(96),
  MONSTER_EGG(97),
  STONEBRICK(98),
  BROWN_MUSHROOM_BLOCK(99),
  RED_MUSHROOM_BLOCK(100),
  IRON_BARS(101),
  GLASS_PANE(102),
  MELON_BLOCK(103),
  PUMPKIN_STEM(104),
  MELON_STEM(105),
  VINE(106),
  FENCE_GATE(107),
  BRICK_STAIRS(108),
  STONE_BRICK_STAIRS(109),
  MYCELIUM(110),
  WATERLILY(111),
  NETHER_BRICK(112),
  NETHER_BRICK_FENCE(113),
  NETHER_BRICK_STAIRS(114),
  NETHER_WART(115),
  ENCHANTING_TABLE(116),
  BREWING_STAND(117),
  CAULDRON(118),
  END_PORTAL(119),
  END_PORTAL_FRAME(120),
  END_STONE(121),
  DRAGON_EGG(122),
  REDSTONE_LAMP(123),
  LIT_REDSTONE_LAMP(124),
  DOUBLE_WOODEN_SLAB(125),
  WOODEN_SLAB(126),
  COCOA(127),
  SANDSTONE_STAIRS(128),
  EMERALD_ORE(129),
  ENDER_CHEST(130),
  TRIPWIRE_HOOK(131),
  TRIPWIRE(132),
  EMERALD_BLOCK(133),
  SPRUCE_STAIRS(134),
  BIRCH_STAIRS(135),
  JUNGLE_STAIRS(136),
  COMMAND_BLOCK(137),
  BEACON(138),
  COBBLESTONE_WALL(139),
  FLOWER_POT(140),
  CARROTS(141),
  POTATOES(142),
  WOODEN_BUTTON(143),
  SKULL(144),
  ANVIL(145),
  TRAPPED_CHEST(146),
  LIGHT_WEIGHTED_PRESSURE_PLATE(147),
  HEAVY_WEIGHTED_PRESSURE_PLATE(148),
  UNPOWERED_COMPARATOR(149),
  POWERED_COMPARATOR(150),
  DAYLIGHT_DETECTOR(151),
  REDSTONE_BLOCK(152),
  QUARTZ_ORE(153),
  HOPPER(154),
  QUARTZ_BLOCK(155),
  QUARTZ_STAIRS(156),
  ACTIVATOR_RAIL(157),
  DROPPER(158),
  STAINED_HARDENED_CLAY(159),
  STAINED_GLASS_PANE(160),
  LEAVES2(161),
  LOG2(162),
  ACACIA_STAIRS(163),
  DARK_OAK_STAIRS(164),
  SLIME(165),
  BARRIER(166),
  IRON_TRAPDOOR(167),
  PRISMARINE(168),
  SEA_LANTERN(169),
  HAY_BLOCK(170),
  CARPET(171),
  HARDENED_CLAY(172),
  COAL_BLOCK(173),
  PACKED_ICE(174),
  DOUBLE_PLANT(175),
  STANDING_BANNER(176),
  WALL_BANNER(177),
  DAYLIGHT_DETECTOR_INVERTED(178),
  RED_SANDSTONE(179),
  RED_SANDSTONE_STAIRS(180),
  DOUBLE_STONE_SLAB2(181),
  STONE_SLAB2(182),
  SPRUCE_FENCE_GATE(183),
  BIRCH_FENCE_GATE(184),
  JUNGLE_FENCE_GATE(185),
  DARK_OAK_FENCE_GATE(186),
  ACACIA_FENCE_GATE(187),
  SPRUCE_FENCE(188),
  BIRCH_FENCE(189),
  JUNGLE_FENCE(190),
  DARK_OAK_FENCE(191),
  ACACIA_FENCE(192),
  SPRUCE_DOOR(193),
  BIRCH_DOOR(194),
  JUNGLE_DOOR(195),
  ACACIA_DOOR(196),
  DARK_OAK_DOOR(197),
  END_ROD(198),
  CHORUS_PLANT(199),
  CHORUS_FLOWER(200),
  PURPUR_BLOCK(201),
  PURPUR_PILLAR(202),
  PURPUR_STAIRS(203),
  PURPUR_DOUBLE_SLAB(204),
  PURPUR_SLAB(205),
  END_BRICKS(206),
  BEETROOTS(207),
  GRASS_PATH(208),
  END_GATEWAY(209),
  REPEATING_COMMAND_BLOCK(210),
  CHAIN_COMMAND_BLOCK(211),
  FROSTED_ICE(212),
  MAGMA(213),
  NETHER_WART_BLOCK(214),
  RED_NETHER_BRICK(215),
  BONE_BLOCK(216),
  STRUCTURE_VOID(217),
  OBSERVER(218),
  WHITE_SHULKER_BOX(219),
  ORANGE_SHULKER_BOX(220),
  MAGENTA_SHULKER_BOX(221),
  LIGHT_BLUE_SHULKER_BOX(222),
  YELLOW_SHULKER_BOX(223),
  LIME_SHULKER_BOX(224),
  PINK_SHULKER_BOX(225),
  GRAY_SHULKER_BOX(226),
  SILVER_SHULKER_BOX(227),
  CYAN_SHULKER_BOX(228),
  PURPLE_SHULKER_BOX(229),
  BLUE_SHULKER_BOX(230),
  BROWN_SHULKER_BOX(231),
  GREEN_SHULKER_BOX(232),
  RED_SHULKER_BOX(233),
  BLACK_SHULKER_BOX(234),
  WHITE_GLAZED_TERRACOTTA(235),
  ORANGE_GLAZED_TERRACOTTA(236),
  MAGENTA_GLAZED_TERRACOTTA(237),
  LIGHT_BLUE_GLAZED_TERRACOTTA(238),
  YELLOW_GLAZED_TERRACOTTA(239),
  LIME_GLAZED_TERRACOTTA(240),
  PINK_GLAZED_TERRACOTTA(241),
  GRAY_GLAZED_TERRACOTTA(242),
  SILVER_GLAZED_TERRACOTTA(243),
  CYAN_GLAZED_TERRACOTTA(244),
  PURPLE_GLAZED_TERRACOTTA(245),
  BLUE_GLAZED_TERRACOTTA(246),
  BROWN_GLAZED_TERRACOTTA(247),
  GREEN_GLAZED_TERRACOTTA(248),
  RED_GLAZED_TERRACOTTA(249),
  BLACK_GLAZED_TERRACOTTA(250),
  CONCRETE(251),
  CONCRETE_POWDER(252),
  STRUCTURE_BLOCK(255);

  private final int id;

  private byte data;

  Block(final int id) {
    this.id = id;
    this.data = 0;
  }

  Block(final int id, byte data) {
    this.id = id;
    this.data = data;
  }

  public int getId() {
    return id;
  }

  public byte getData() {
    return data;
  }
}