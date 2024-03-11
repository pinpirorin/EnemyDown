package plugin.enemydown.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SplittableRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.event.Listener;
import plugin.enemydown.Main;
import plugin.enemydown.data.PlayerScore;

/*
 * 制限時間内にランダムで出現する敵を倒して、スコアを獲得するゲームを起動するゲームです
 */
public class EnemyDownCommand extends BaseCommand implements Listener {

  public static final int GAME_TIME = 20;
  private Main main;
  private List<PlayerScore> playerScoreList = new ArrayList<>();
  private List<Entity> spawnEntitylist = new ArrayList<>();

  public EnemyDownCommand(Main main) {
    this.main = main;
  }

  @Override
  public boolean onExecutePlayerCommand(Player player) {
    PlayerScore nowplayerScore = getPlayerScore(player);

    initPlayerStatus(player);

    gamePlay(player, nowplayerScore);
    return true;
}
  @Override
  public boolean onExecuteNPCCommand(CommandSender sender) {
  return false;
  }

  @EventHandler
  public void onEnemyDeath(EntityDeathEvent e) {
    LivingEntity enemy = e.getEntity();
    Player player = enemy.getKiller();
    if(Objects.isNull(player) || playerScoreList.isEmpty()) {
      return;
    }

    for (PlayerScore playerScore : playerScoreList) {
      if (playerScore.getPlayerName().equals(player.getName())) {
        int point = switch (enemy.getType()) {
          case ZOMBIE -> 10;
          case SKELETON, WITCH -> 20;
          default -> 0;
        };

        playerScore.setScore(playerScore.getScore() + point);
        player.sendMessage("敵を倒した！現在のスコアは" + playerScore.getScore() + "点！");
      }
    }
  }

  /**
   * 現在実行しているプレイヤーのスコア情報を取得する
   *
   * @param player コマンドを実行したプレイヤー
   * @return 現在実行しているプレイヤーのスコア情報
   */
  private PlayerScore getPlayerScore(Player player) {
    PlayerScore playerScore = new PlayerScore(player.getName());

    if (playerScoreList.isEmpty()) {
      playerScore = addNewPlayer(player);
    } else {
      playerScore = playerScoreList.stream()
          .findFirst()
          .map(ps -> ps.getPlayerName().equals(player.getName())
              ? ps
              : addNewPlayer(player)).orElse(playerScore);
    }

    playerScore.setGameTime(GAME_TIME);
    playerScore.setScore(0);
    return playerScore;
  }

  /**
   *新規のプレイヤー情報をリストに追加します
   *
   * @param player　コマンドを実行したプレイヤー
   * @return 新規プレイヤー
   */
  private PlayerScore addNewPlayer(Player player) {
    PlayerScore newPlayer = new PlayerScore(player.getName());
    playerScoreList.add(newPlayer);
    return  newPlayer;
  }

  /**
   * ゲームを始める前にプレイヤーの状態を設定すす
   * 体力と空腹度を最大にして、装備はネザライト一式になる
   *
   * @param player　コマンドを実行したプレイヤー
   */
  private static void initPlayerStatus(Player player) {
    player.setHealth(20);
    player.setFoodLevel(20);

    PlayerInventory inventory = player.getInventory();
    inventory.setHelmet(new ItemStack(Material.DIAMOND_HELMET));
    inventory.setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
    inventory.setLeggings(new ItemStack(Material.DIAMOND_LEGGINGS));
    inventory.setBoots(new ItemStack(Material.DIAMOND_BOOTS));
    inventory.setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
  }


/**
 * ゲームを実行します。規定の時間内に敵を倒すと
 * @param player
 * @param nowPlayerScore
 */
private void gamePlay(Player player, PlayerScore nowPlayerScore) {
  Bukkit.getScheduler().runTaskTimer(main, Runnable -> {
    if (nowPlayerScore.getGameTime() <= 0) {
      Runnable.cancel();

      player.sendTitle("ゲームを終了しました",
          nowPlayerScore.getPlayerName() + " 合計 " + nowPlayerScore.getScore()+"点!",
          0,60,0);

      spawnEntitylist.forEach(Entity::remove);
      return;
    }
    Entity spawnEntity = player.getWorld().spawnEntity(getEnemySpawnLocation(player), getEnemy());
    spawnEntitylist.add(spawnEntity);
    nowPlayerScore.setGameTime(nowPlayerScore.getGameTime() - 5);
  }, 0, 5 * 20);
}
  /**
   * 敵の出現場所を取得します 出現エリアはｘ軸とｙ軸は自分の位置からプラス、ランダムで-10～9の値が設定されます ｙ軸はプレイヤーと同じ位置になります
   *
   * @param player 　コマンドを実行したプレイヤー
   * @return　敵の出現場所
   */
  private Location getEnemySpawnLocation(Player player) {
    Location playerLocation = player.getLocation();
    int randomX = new SplittableRandom().nextInt(20) - 10;
    int randomZ = new SplittableRandom().nextInt(20) - 10;

    double x = playerLocation.getX() + randomX;
    double y = playerLocation.getY();
    double z = playerLocation.getZ() + randomZ;

    return new Location(player.getWorld(), x, y, z);
  }

  /**
   * ランダムで敵を抽出して。その結果の敵を取得する
   *
   * @return　敵
   */
  private EntityType getEnemy() {
    List<EntityType> enemyList = List.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.WITCH);
    return enemyList.get(new SplittableRandom().nextInt(enemyList.size()));

  }
}
