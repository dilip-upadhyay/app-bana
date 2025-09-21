import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';

@Component({
  selector: 'ab-card',
  standalone: true,
  imports: [CommonModule, MatCardModule],
  templateUrl: './card.html',
  styleUrl: './card.css'
})
export class Card {
  @Input() title: string = '';
  @Input() subtitle: string = '';
}
