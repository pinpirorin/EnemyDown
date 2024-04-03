package plugin.enemydown.mapper.data.data;

import lombok.Getter;
import lombok.Setter;

/**
 * プレイヤースコア情報を扱うオブジェクト
 * DBに存在するテーブルと連動する
 *
 */
@Getter
@Setter
public class PlayerScore {

  private int id;
  private String playerName;
  private int score;
  private String difficulty;
  private String registeredDt;

}
