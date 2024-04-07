package plugin.enemydown.command;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SplittableRandom;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
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
import org.bukkit.potion.PotionEffect;
import plugin.enemydown.Main;
import plugin.enemydown.data.ExecutingPlayer;
import plugin.enemydown.mapper.PlayerScoreMapper;
import plugin.enemydown.mapper.data.PlayerScore;

/*
 * 制限時間内にランダムで出現する敵を倒して、スコアを獲得するゲームを起動するゲームです
 */
public class EnemyDownCommand extends BaseCommand implements Listener {

  public static final int GAME_TIME = 20;
  public static final String EASY = "easy";
  public static final String NORMAL = "normal";
  public static final String HARD = "hard";
  public static final String NONE = "none";

  public static final String LIST = "list";

  private Main main;
  private List<ExecutingPlayer> executingPlayerList = new ArrayList<>();
  private List<Entity> spawnEntitylist = new ArrayList<>();

  private SqlSessionFactory sqlSessionFactory;

  public EnemyDownCommand(Main main) {
    this.main = main;
  }

  private SqlSessionFactory buildSqlSessionFactory(){

    {
      try {
        InputStream inputStream = Resources.getResourceAsStream("mybatis-config.xml");
        return new SqlSessionFactoryBuilder().build(inputStream);
      } catch (Exception e) {
        throw new RuntimeException("Failed to initialize SqlSessionFactory", e);
      }
    }
  }
  @Override
  public boolean onExecutePlayerCommand(Player player, Command command, String label,
      String[] args) {
    if (args.length == 1 && LIST.equals(args[0])) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
          PlayerScoreMapper mapper = session.getMapper(PlayerScoreMapper.class);
          List<PlayerScore> playerScoreList = mapper.selectList();

          DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
          for (PlayerScore playerScore : playerScoreList) {
            if (playerScore.getRegisteredDt()!=null) {
              LocalDateTime date = LocalDateTime.parse(playerScore.getRegisteredDt(), formatter);
              player.sendMessage(playerScore.getId() + " | "
                  + playerScore.getPlayerName() + " | "
                  + playerScore.getScore() + " | "
                  + playerScore.getDifficulty() + " | "
                  + date.format(formatter));
            }else {

            }
          }
        }
        return false;
      }
      String difficulty = getDifficulty(player, args);
      if (difficulty.equals(NONE)) {
        return false;
      }

      ExecutingPlayer nowExecutingPlayer = getPlayerScore(player);

      initPlayerStatus(player);
      gamePlay(player, nowExecutingPlayer, difficulty);
      return true;
    }

  /**
   * 難易度をコマンド引数から取得します
   *
   * @param player 　コマンドを実行したプレイヤー
   * @param args   　コマンド引数
   * @return　難易度
   */
  private String getDifficulty(Player player, String[] args) {
    if (args.length == 1 && (EASY.equals(args[0]) || NORMAL.equals(args[0]) || HARD.equals(
        args[0]))) {
      return args[0];
    }
    player.sendMessage(ChatColor.RED + "実行できませんコマンド引数一つ目に難易度設定が必要です。［easy,normal,hard］");
    return NONE;
  }

  @Override
  public boolean onExecuteNPCCommand(CommandSender sender, Command command, String label, String[] args) {
  return false;
  }

  @EventHandler
  public void onEnemyDeath(EntityDeathEvent e) {
    LivingEntity enemy = e.getEntity();
    Player player = enemy.getKiller();

    if(Objects.isNull(player) || spawnEntitylist.stream().noneMatch(entity -> entity.equals(enemy))) {
      return;
    }

    executingPlayerList.stream()
        .filter(p -> p.getPlayerName().equals(player.getName()))
        .findFirst()
        .ifPresent(p ->{
          int point = switch (enemy.getType()) {
            case ZOMBIE -> 10;
            case SKELETON, WITCH -> 20;
            default -> 0;
          };

          p.setScore(p.getScore() + point);
          player.sendMessage("敵を倒した！現在のスコアは" + p.getScore() + "点！");
        });
  }

  /**
   * 現在実行しているプレイヤーのスコア情報を取得する
   *
   * @param player コマンドを実行したプレイヤー
   * @return 現在実行しているプレイヤーのスコア情報
   */
  private ExecutingPlayer getPlayerScore(Player player) {
    ExecutingPlayer executingPlayer = new ExecutingPlayer(player.getName());

    if (executingPlayerList.isEmpty()) {
      executingPlayer = addNewPlayer(player);
    } else {
      executingPlayer = executingPlayerList.stream()
          .findFirst()
          .map(ps -> ps.getPlayerName().equals(player.getName())
              ? ps
              : addNewPlayer(player)).orElse(executingPlayer);
    }

    executingPlayer.setGameTime(GAME_TIME);
    executingPlayer.setScore(0);
    removePotionEffect(player);
    return executingPlayer;
  }

  /**
   * プレイヤーに設定されている特殊効果を除外します
   *
   * @param player　コマンドを実行したプレイヤー
   */
  private void removePotionEffect(Player player) {
    player.getActivePotionEffects().stream()
        .map(PotionEffect::getType)
        .forEach(player::removePotionEffect);
  }

  /**
   *新規のプレイヤー情報をリストに追加します
   *
   * @param player　コマンドを実行したプレイヤー
   * @return 新規プレイヤー
   */
  private ExecutingPlayer addNewPlayer(Player player) {
    ExecutingPlayer newPlayer = new ExecutingPlayer(player.getName());
    executingPlayerList.add(newPlayer);
    return  newPlayer;
  }

  /**
   * ゲームを始める前にプレイヤーの状態を設定すす
   * 体力と空腹度を最大にして、装備はネザライト一式になる
   *
   * @param player　コマンドを実行したプレイヤー
   */
  private void initPlayerStatus(Player player) {
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
 * @param nowExecutingPlayer
 * @param  difficulty 難易度
 */
private void gamePlay(Player player, ExecutingPlayer nowExecutingPlayer, String difficulty) {
  Bukkit.getScheduler().runTaskTimer(main, Runnable -> {
    if (nowExecutingPlayer.getGameTime() <= 0) {
      Runnable.cancel();

      player.sendTitle("ゲームを終了しました",
          nowExecutingPlayer.getPlayerName() + " 合計 " + nowExecutingPlayer.getScore() + "点!",
          0, 60, 0);

      try (Connection con = DriverManager.getConnection(
          "jdbc:mysql://localhost:3306/spigot_server?serverTimezone=UTC",
          "root",
          "yasuzaki77**");
          Statement statement = con.createStatement()){

        statement.executeUpdate(
            "insert player_score(player_name,score,difficulty,registeredDt)"
            +"values('"+nowExecutingPlayer.getPlayerName()+"',"+nowExecutingPlayer.getScore()
            +difficulty+"',now());");

      } catch (SQLException e) {
        e.printStackTrace();
      }
      spawnEntitylist.forEach(Entity::remove);
      spawnEntitylist.clear();

      removePotionEffect(player);
      return;
    }
    Entity spawnEntity = player.getWorld().spawnEntity(getEnemySpawnLocation(player), getEnemy(difficulty));
    spawnEntitylist.add(spawnEntity);
    nowExecutingPlayer.setGameTime(nowExecutingPlayer.getGameTime() - 5);
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
   * @param difficulty 難易度
   * @return　敵
   *
   */
  private EntityType getEnemy(String difficulty) {
    List<EntityType> enemyList = switch (difficulty) {
      case NORMAL -> List.of(EntityType.ZOMBIE, EntityType.SKELETON);
      case HARD -> List.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.WITCH);
      default -> List.of(EntityType.ZOMBIE);
    };
    return enemyList.get(new SplittableRandom().nextInt(enemyList.size()));

  }
}
